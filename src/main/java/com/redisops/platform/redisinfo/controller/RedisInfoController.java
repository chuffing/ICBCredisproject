package com.redisops.platform.redisinfo.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import com.redisops.platform.redisinfo.model.InitializeRedisInfoRequest;
import com.redisops.platform.redisinfo.service.RedisInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 查询和首次初始化平台正式纳管的Redis实例。
 */
@RestController
@RequestMapping("/api/redis-info")
public class RedisInfoController {

    private final RedisInfoService redisInfoService;

    /** 注入Redis实例信息服务。 */
    public RedisInfoController(RedisInfoService redisInfoService) {
        this.redisInfoService = redisInfoService;
    }

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
            return ResponseEntity.ok(ApiResponse.success(
                    "正式Redis拓扑初始化成功", redisInfoService.initialize(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("REDIS_INFO_INITIALIZE_FAILED", e.getMessage()));
        }
    }
}
