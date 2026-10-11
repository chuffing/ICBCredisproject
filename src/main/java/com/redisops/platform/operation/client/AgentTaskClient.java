package com.redisops.platform.operation.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.operation.model.AgentTaskRequest;
import com.redisops.platform.operation.config.AgentClientProperties;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotFiles;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * 平台向执行器下发原子操作的 HTTP 客户端。
 */
@Service
public class AgentTaskClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AgentRegistrationService agentRegistrationService;
    private final VersionPackageService versionPackageService;
    private final ExecutorConfigSnapshotService configSnapshotService;
    private final AgentClientProperties clientProperties;

    /** 注入 HTTP、JSON 和执行器认证组件。 */
    public AgentTaskClient(RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           AgentRegistrationService agentRegistrationService,
                           VersionPackageService versionPackageService,
                           ExecutorConfigSnapshotService configSnapshotService,
                           AgentClientProperties clientProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.agentRegistrationService = agentRegistrationService;
        this.versionPackageService = versionPackageService;
        this.configSnapshotService = configSnapshotService;
        this.clientProperties = clientProperties;
    }

    /**
     * 根据操作类型组装任务请求，并通过HTTP下发给目标执行器。
     *
     * @param executor 目标执行器
     * @param taskNo 业务任务编号
     * @param stepNo 步骤序号
     * @param operationType 原子操作类型
     * @param paramsJson 参数 JSON
     */
    public void dispatch(ExecutorInfo executor, String taskNo, Integer stepNo,
                         String operationType, String paramsJson) {

        try {
            AgentTaskRequest request = new AgentTaskRequest();
            request.setTaskNo(taskNo);
            request.setStepNo(stepNo);
            request.setTaskType(operationType);
            request.setParams(readParams(paramsJson));

            ResponseEntity<String> response;
            if (AgentOperationType.DEPLOY_VERSION.name().equals(operationType)) {
                // 平台需要向执行器发送安装包。
                response = dispatchVersionPackage(executor, request);
            } else if (AgentOperationType.DEPLOY_CONFIG.name().equals(operationType)) {
                // 平台需要向执行器发送配置文件。
                response = dispatchConfigFiles(executor, request);
            } else {
                // 平台只需要向执行器发送JSON参数。
                response = dispatchJsonTask(executor, request);
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("执行器拒绝任务，HTTP 状态="
                        + response.getStatusCodeValue());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用执行器任务接口失败", e);
        }
    }

    /**
     * 主动查询执行器本地保存的最终结果。
     *
     * <p>该接口用于修复“实际操作已经完成，但结果回调在网络中丢失”的情况。
     * 执行器返回404表示该步骤尚未产生最终结果。</p>
     *
     * @param executor 目标执行器
     * @param taskNo 业务任务编号
     * @param stepNo 步骤序号
     * @return 已保存的最终结果；未找到时返回null
     */
    public TaskResultReportRequest queryTaskResult(ExecutorInfo executor,
                                                   String taskNo,
                                                   Integer stepNo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(
                        agentBaseUrl(executor) + clientProperties.getTaskResultPath())
                .queryParam("taskNo", taskNo)
                .queryParam("stepNo", stepNo)
                .build(true)
                .toUri();
        try {
            ResponseEntity<TaskResultReportRequest> response = restTemplate.exchange(
                    uri, HttpMethod.GET,
                    new HttpEntity<Void>(authorizedHeaders(executor)),
                    TaskResultReportRequest.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /** 通过HTTP POST向执行器发送普通JSON运维任务。 */
    private ResponseEntity<String> dispatchJsonTask(ExecutorInfo executor,
                                                    AgentTaskRequest request) {

        // 读取执行器的认证token，创建HTTP请求头
        HttpHeaders headers = authorizedHeaders(executor);
        // 指定请求体格式
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 构造执行器地址
        String url = agentBaseUrl(executor) + clientProperties.getTaskPath();
        // 发送POST请求
        return restTemplate.postForEntity(
                url,
                new HttpEntity<>(request, headers), // 用来封装发送给执行器的HTTP请求体和请求头。
                String.class); // 希望把响应体解析为什么类型
    }

    /**
     * 使用 multipart 接口同时发送 DEPLOY_VERSION 任务 JSON 和原始 tar.gz 文件。
     */
    private ResponseEntity<String> dispatchVersionPackage(ExecutorInfo executor,
                                                          AgentTaskRequest request) {
        Map<String, String> params = request.getParams();
        String version = params == null ? null : params.get("version");
        String packageSha256 = params == null ? null : params.get("packageSha256");
        Path packageFile = versionPackageService.requirePackageFile(version, packageSha256);

        HttpHeaders taskHeaders = new HttpHeaders();
        taskHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AgentTaskRequest> taskPart = new HttpEntity<>(request, taskHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("task", taskPart);
        body.add("file", new FileSystemResource(packageFile.toFile()));

        HttpHeaders requestHeaders = authorizedHeaders(executor);
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        String url = agentBaseUrl(executor) + clientProperties.getVersionTaskPath();
        return restTemplate.postForEntity(
                url, new HttpEntity<>(body, requestHeaders), String.class);
    }

    /**
     * 解密平台基准配置，并通过 Agent 的配置 multipart 接口下发两份文件。
     */
    private ResponseEntity<String> dispatchConfigFiles(ExecutorInfo executor,
                                                       AgentTaskRequest request) {
        ConfigSnapshotFiles files = configSnapshotService
                .readActiveConfigFiles(executor.getExecutorId());
        verifyConfigParams(request.getParams(), files);

        HttpHeaders taskHeaders = new HttpHeaders();
        taskHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AgentTaskRequest> taskPart = new HttpEntity<>(request, taskHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("task", taskPart);
        body.add("redisFile", namedResource(files.getRedisConfig(), "redis.conf"));
        body.add("sentinelFile", namedResource(
                files.getSentinelConfig(), "sentinel.conf"));

        HttpHeaders requestHeaders = authorizedHeaders(executor);
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        String url = agentBaseUrl(executor) + clientProperties.getConfigTaskPath();
        return restTemplate.postForEntity(
                url, new HttpEntity<>(body, requestHeaders), String.class);
    }

    /** 下发前确认任务记录中的摘要仍与平台当前可信配置一致。 */
    private void verifyConfigParams(Map<String, String> params,
                                    ConfigSnapshotFiles files) {
        String redisSha256 = params == null ? null : params.get("redisConfigSha256");
        String sentinelSha256 = params == null
                ? null : params.get("sentinelConfigSha256");
        if (!files.getRedisConfigSha256().equals(redisSha256)
                || !files.getSentinelConfigSha256().equals(sentinelSha256)) {
            throw new IllegalStateException("服务器切换任务中的配置摘要与平台基准配置不一致");
        }
    }

    /** 创建带固定安全文件名的内存资源，避免把平台本地路径发送给 Agent。 */
    private ByteArrayResource namedResource(byte[] content, String fileName) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    /** 创建包含目标执行器 Bearer Token 的请求头。 */
    private HttpHeaders authorizedHeaders(ExecutorInfo executor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentRegistrationService.readDispatchToken(executor));
        return headers;
    }

    /** 根据配置协议和执行器注册地址构造访问根地址。 */
    private String agentBaseUrl(ExecutorInfo executor) {
        return clientProperties.getScheme() + "://"
                + executor.getHostIp() + ":" + executor.getExecutorPort();
    }

    /** 把数据库中的参数 JSON 转换为执行器需要的 Map。 */
    private Map<String, String> readParams(String paramsJson) throws Exception {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(paramsJson, new TypeReference<Map<String, String>>() { });
    }
}
