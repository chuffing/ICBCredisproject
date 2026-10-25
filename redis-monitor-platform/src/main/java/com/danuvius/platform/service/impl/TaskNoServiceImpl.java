package com.danuvius.platform.service.impl;

import com.danuvius.platform.common.constant.OperationTaskConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.TaskNoSequenceMapper;
import com.danuvius.platform.service.TaskNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 运维任务编号服务实现。
 */
@Service
@RequiredArgsConstructor
public class TaskNoServiceImpl implements TaskNoService {

    private final TaskNoSequenceMapper taskNoSequenceMapper;

    /**
     * 生成下一个运维任务编号。
     *
     * @return 运维任务编号
     */
    @Override
    public String nextTaskNo() {

        // 1. 获取当前业务日期和当天的下一个流水号。
        LocalDate bizDate = LocalDate.now();
        Integer sequence = taskNoSequenceMapper.nextValue(bizDate);

        // 2. 校验数据库返回值和当天流水号上限。
        if (sequence == null
                || sequence <= 0
                || sequence > OperationTaskConstants.MAX_DAILY_TASK_SEQUENCE) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当天运维任务数量已达到上限"
            );
        }

        // 3. 按照TASK加日期和四位流水号的格式生成任务编号。
        return OperationTaskConstants.TASK_NO_PREFIX
                + bizDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", sequence);
    }
}
