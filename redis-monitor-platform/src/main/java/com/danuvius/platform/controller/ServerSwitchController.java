package com.danuvius.platform.controller;

import cn.hutool.core.lang.Validator;
import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.ServerSwitchRequestDTO;
import com.danuvius.platform.model.vo.ServerSwitchResponseVO;
import com.danuvius.platform.service.ServerSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis服务器切换接口。
 */
@RestController
@RequestMapping("/api/serverSwitch")
@RequiredArgsConstructor
public class ServerSwitchController {

    private final ServerSwitchService serverSwitchService;

    /**
     * 创建完整的Redis服务器切换任务。
     */
    @PostMapping
    public ResultBean<ServerSwitchResponseVO> switchServer(
            @RequestBody ServerSwitchRequestDTO request) {

        validateRequest(request);

        ServerSwitchResponseVO response = serverSwitchService.switchServer(request);

        return new ResultBean<>(
                HttpStatus.HTTP_ACCEPTED,
                "服务器切换任务已接收",
                response
        );
    }

    /**
     * 查询服务器切换任务状态。
     */
    @GetMapping("/{taskNo}")
    public ResultBean<ServerSwitchResponseVO> getSwitchStatus(
            @PathVariable String taskNo) {

        String normalizedTaskNo = taskNo == null ? null : taskNo.trim();
        if (normalizedTaskNo == null
                || !ValidationConstants.TASK_NO_PATTERN
                .matcher(normalizedTaskNo).matches()) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_NO_INVALID,
                    "任务编号格式不正确"
            );
        }

        return ResultBean.success(
                serverSwitchService.getSwitchStatus(normalizedTaskNo)
        );
    }

    private void validateRequest(ServerSwitchRequestDTO request) {

        if (request == null || request.getPackageId() == null
                || request.getPackageId() <= 0) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "安装包编号必须大于0"
            );
        }

        String masterHostIp = request.getMasterHostIp() == null
                ? null : request.getMasterHostIp().trim();
        if (!Validator.isIpv4(masterHostIp)) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "目标主节点服务器IP格式不正确"
            );
        }

        List<String> replicaHostIps = request.getReplicaHostIps();
        if (replicaHostIps == null
                || replicaHostIps.size()
                != ServerSwitchConstants.REPLICA_NODE_COUNT) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "必须提供两个目标从节点服务器IP"
            );
        }

        Set<String> targetHostIps = new HashSet<>();
        targetHostIps.add(masterHostIp);
        for (int index = 0; index < replicaHostIps.size(); index++) {

            String hostIp = replicaHostIps.get(index) == null
                    ? null : replicaHostIps.get(index).trim();

            if (!Validator.isIpv4(hostIp)) {
                throw new ParameterException(
                        ParameterErrorCode.TASK_PARAM_INVALID,
                        "目标从节点服务器IP格式不正确"
                );
            }
            if (!targetHostIps.add(hostIp)) {
                throw new ParameterException(
                        ParameterErrorCode.TASK_PARAM_INVALID,
                        "三台目标服务器IP不能重复"
                );
            }
            replicaHostIps.set(index, hostIp);
        }

        if (request.getOperationReason() == null
                || request.getOperationReason().trim().isEmpty()
                || request.getOperationReason().trim().length()
                > ValidationConstants.MAX_OPERATION_REASON_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "操作原因不能为空且不能超过"
                            + ValidationConstants.MAX_OPERATION_REASON_LENGTH
                            + "个字符"
            );
        }

        if (request.getCreatedBy() == null
                || request.getCreatedBy().trim().isEmpty()
                || request.getCreatedBy().trim().length()
                > ValidationConstants.MAX_CREATED_BY_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "操作人不能为空且不能超过"
                            + ValidationConstants.MAX_CREATED_BY_LENGTH
                            + "个字符"
            );
        }

        request.setMasterHostIp(masterHostIp);
        request.setOperationReason(request.getOperationReason().trim());
        request.setCreatedBy(request.getCreatedBy().trim());
    }
}
