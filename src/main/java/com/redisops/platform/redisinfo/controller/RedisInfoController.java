package com.redisops.platform.redisinfo.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import com.redisops.platform.redisinfo.model.InitializeRedisInfoRequest;
import com.redisops.platform.redisinfo.model.RedisInfoNodeRequest;
import com.redisops.platform.redisinfo.service.RedisInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 查询和首次初始化平台正式纳管的Redis实例。
 */
@RestController
@RequestMapping("/api/redis-info")
@RequiredArgsConstructor
public class RedisInfoController {

    private final RedisInfoService redisInfoService;

    /** 查询当前正式纳管的三台实例；密码密文字段由实体上的JsonIgnore排除。 */
    @GetMapping("/managed")
    public ApiResponse<List<RedisInfo>> listManaged() {
        return ApiResponse.success("查询成功", redisInfoService.listManaged());
    }

    /** 首次登记公司已有拓扑；已有正式拓扑后不能重复调用。 */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<List<RedisInfo>>> initialize(
            @RequestBody InitializeRedisInfoRequest request) {
        try {
            // 校验初始化字段；正式拓扑和执行器状态由业务层检查。
            validateInitializeRequest(request);
            return ResponseEntity.ok(ApiResponse.success(
                    "正式Redis拓扑初始化成功", redisInfoService.initialize(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("REDIS_INFO_INITIALIZE_FAILED", e.getMessage()));
        }
    }

    /** 校验首次初始化请求中的节点、角色、端口和密码。 */
    private void validateInitializeRequest(InitializeRedisInfoRequest request) {
        if (request == null || request.getNodes() == null
                || request.getNodes().size() != 3) {
            throw new IllegalArgumentException("初始化正式拓扑必须包含3台服务器");
        }
        if (!StringUtils.hasText(request.getRedisPassword())) {
            throw new IllegalArgumentException("redisPassword不能为空");
        }
        if (!StringUtils.hasText(request.getRedisVersion())) {
            throw new IllegalArgumentException("redisVersion不能为空");
        }
        validatePort(request.getRedisPort(), "redisPort");
        validatePort(request.getSentinelPort(), "sentinelPort");
        Set<String> hostIps = new HashSet<>();
        for (RedisInfoNodeRequest node : request.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getHostIp())) {
                throw new IllegalArgumentException("每台服务器的hostIp都不能为空");
            }
            if (!hostIps.add(node.getHostIp().trim())) {
                throw new IllegalArgumentException("服务器IP不能重复: " + node.getHostIp().trim());
            }
            if (!StringUtils.hasText(node.getRole())) {
                throw new IllegalArgumentException("Redis角色不能为空");
            }
            String role = node.getRole().trim().toUpperCase(Locale.ROOT);
            if (!"MASTER".equals(role) && !"REPLICA".equals(role)) {
                throw new IllegalArgumentException("Redis角色只能是MASTER或REPLICA");
            }
        }
    }

    /** 校验可选端口的合法范围。 */
    private void validatePort(Integer port, String fieldName) {
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException(fieldName + "必须在1到65535之间");
        }
    }
}
