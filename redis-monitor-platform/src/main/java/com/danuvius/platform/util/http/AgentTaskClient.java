package com.danuvius.platform.util.http;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpResource;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.core.io.resource.BytesResource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.constant.AgentTaskConstants;
import com.danuvius.platform.common.exception.AgentTaskDispatchUnknownException;
import com.danuvius.platform.common.exception.AgentTaskRejectedException;
import com.danuvius.platform.model.dto.AgentTaskRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.enums.AgentTaskResponseCodeEnum;
import com.danuvius.platform.model.enums.AgentTaskSubmitStatusEnum;
import com.danuvius.platform.model.vo.AgentTaskSubmitResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 负责向执行器下发运维任务。
 */
@Slf4j
@Component
public class AgentTaskClient {

    /**
     * 向指定执行器下发运维任务。
     *
     * @param executor 执行器信息
     * @param request 运维任务报文
     */
    public void dispatchTask(
            ExecutorInfo executor,
            AgentTaskRequestDTO request) {

        // 根据执行器的IP合端口拼接接口地址。
        String url = "http://"
                + executor.getHostIp()
                + ":"
                + executor.getExecutorPort()
                + AgentTaskConstants.TASK_PATH;

        // 创建并发送HTTP请求，得到执行器response。
        try (HttpResponse response = HttpRequest.post(url)
                .contentType(ContentType.JSON.getValue())
                .header(
                        AgentTaskConstants.AUTHORIZATION_HEADER,
                        AgentTaskConstants.BEARER_PREFIX
                                + executor.getExecutorToken()
                )
                .body(JSON.toJSONString(request))
                .timeout(AgentTaskConstants.TIMEOUT_MS)
                .execute()) {

            // 获得真实HTTP状态码。
            int statusCode = response.getStatus();
            // 获取HTTP响应体。
            String responseBody = response.body();

            validateAcceptedResponse(
                    request,
                    statusCode,
                    responseBody);

        } catch (AgentTaskRejectedException
                 | AgentTaskDispatchUnknownException e) {

            throw e;

        } catch (Exception e) {
            // HTTP请求超时、连接失败或其他未预期异常。
            log.error(
                    "向执行器下发任务失败，taskNo={}，stepNo={}，url={}",
                    request.getTaskNo(),
                    request.getStepNo(),
                    url,
                    e
            );

            throw new AgentTaskDispatchUnknownException(
                    "无法确认执行器是否收到任务",
                    e
            );
        }
    }

    /**
     * 向指定执行器上传Redis源码包并下发版本部署任务。
     */
    public void dispatchVersionTask(
            ExecutorInfo executor,
            AgentTaskRequestDTO request,
            File packageFile) {

        String url = "http://"
                + executor.getHostIp()
                + ":"
                + executor.getExecutorPort()
                + AgentTaskConstants.VERSION_DEPLOY_PATH;

        byte[] taskBytes = JSON.toJSONString(request)
                .getBytes(StandardCharsets.UTF_8);
        HttpResource taskPart = new HttpResource(
                new BytesResource(taskBytes, "task.json"),
                ContentType.JSON.getValue()
        );

        try (HttpResponse response = HttpRequest.post(url)
                .header(
                        AgentTaskConstants.AUTHORIZATION_HEADER,
                        AgentTaskConstants.BEARER_PREFIX
                                + executor.getExecutorToken()
                )
                .form("task", taskPart)
                .form("file", packageFile, packageFile.getName())
                .timeout(AgentTaskConstants.VERSION_UPLOAD_TIMEOUT_MS)
                .execute()) {

            validateAcceptedResponse(
                    request,
                    response.getStatus(),
                    response.body()
            );

        } catch (AgentTaskRejectedException
                 | AgentTaskDispatchUnknownException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error(
                    "向执行器上传版本安装包失败，taskNo={}，stepNo={}，url={}",
                    request.getTaskNo(),
                    request.getStepNo(),
                    url,
                    exception
            );
            throw new AgentTaskDispatchUnknownException(
                    "无法确认执行器是否收到版本部署任务",
                    exception
            );
        }
    }

    /**
     * 向指定执行器上传Redis、Sentinel配置并下发配置部署任务。
     */
    public void dispatchConfigTask(
            ExecutorInfo executor,
            AgentTaskRequestDTO request,
            File redisConfigFile,
            File sentinelConfigFile) {

        String url = "http://"
                + executor.getHostIp()
                + ":"
                + executor.getExecutorPort()
                + AgentTaskConstants.CONFIG_DEPLOY_PATH;

        byte[] taskBytes = JSON.toJSONString(request)
                .getBytes(StandardCharsets.UTF_8);
        HttpResource taskPart = new HttpResource(
                new BytesResource(taskBytes, "task.json"),
                ContentType.JSON.getValue()
        );

        try (HttpResponse response = HttpRequest.post(url)
                .header(
                        AgentTaskConstants.AUTHORIZATION_HEADER,
                        AgentTaskConstants.BEARER_PREFIX
                                + executor.getExecutorToken()
                )
                .form("task", taskPart)
                .form("redisFile", redisConfigFile,
                        redisConfigFile.getName())
                .form("sentinelFile", sentinelConfigFile,
                        sentinelConfigFile.getName())
                .timeout(AgentTaskConstants.CONFIG_UPLOAD_TIMEOUT_MS)
                .execute()) {

            validateAcceptedResponse(
                    request,
                    response.getStatus(),
                    response.body()
            );

        } catch (AgentTaskRejectedException
                 | AgentTaskDispatchUnknownException exception) {
            throw exception;

        } catch (Exception exception) {
            log.error(
                    "向执行器上传配置文件失败，taskNo={}，stepNo={}，url={}",
                    request.getTaskNo(),
                    request.getStepNo(),
                    url,
                    exception
            );
            throw new AgentTaskDispatchUnknownException(
                    "无法确认执行器是否收到配置部署任务",
                    exception
            );
        }
    }

    /**
     * 校验执行器的HTTP状态码、业务响应码和任务编号。
     */
    private void validateAcceptedResponse(
            AgentTaskRequestDTO request,
            int statusCode,
            String responseBody) {

        if (statusCode != HttpStatus.HTTP_OK) {
            if (statusCode == HttpStatus.HTTP_UNAUTHORIZED) {
                throw new AgentTaskRejectedException(
                        "执行器身份认证失败，任务未接收"
                );
            }
            throw new AgentTaskDispatchUnknownException(
                    "执行器HTTP响应异常，HTTP状态码：" + statusCode
            );
        }

        ResultBean<AgentTaskSubmitResponseVO> responseResult =
                parseResponse(responseBody);
        if (responseResult == null) {
            throw new AgentTaskDispatchUnknownException(
                    "执行器任务接收响应为空或格式错误，HTTP状态码："
                            + statusCode
            );
        }

        AgentTaskResponseCodeEnum responseCode =
                AgentTaskResponseCodeEnum.fromCode(responseResult.getCode());
        if (responseCode == null) {
            throw new AgentTaskDispatchUnknownException(
                    "执行器返回未知业务码：" + responseResult.getCode()
            );
        }
        if (responseCode != AgentTaskResponseCodeEnum.ACCEPTED) {
            String message = responseResult.getMsg();
            if (message == null || message.trim().isEmpty()) {
                message = responseCode.getDefaultMsg();
            }
            throw new AgentTaskRejectedException(message);
        }

        AgentTaskSubmitResponseVO result = responseResult.getData();
        if (result == null) {
            throw new AgentTaskDispatchUnknownException(
                    "执行器已返回接收成功，但任务接收数据为空"
            );
        }
        if (!request.getTaskNo().equals(result.getTaskNo())) {
            throw new AgentTaskDispatchUnknownException(
                    "执行器响应的任务编号与下发任务不一致"
            );
        }
    }


    /**
     * 解析执行器返回的任务接收响应。
     */
    private ResultBean<AgentTaskSubmitResponseVO> parseResponse(
            String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject responseJson = JSON.parseObject(responseBody);
            if (!responseJson.containsKey("code")
                    || !responseJson.containsKey("msg")) {
                return null;
            }

            ResultBean<AgentTaskSubmitResponseVO> responseResult =
                    new ResultBean<>();
            responseResult.setCode(responseJson.getIntValue("code"));
            responseResult.setMsg(responseJson.getString("msg"));
            responseResult.setData(responseJson.getObject(
                    "data",
                    AgentTaskSubmitResponseVO.class
            ));
            return responseResult;
        } catch (Exception e) {
            log.warn("执行器任务接收响应解析失败：{}", responseBody, e);
            return null;
        }
    }
}
