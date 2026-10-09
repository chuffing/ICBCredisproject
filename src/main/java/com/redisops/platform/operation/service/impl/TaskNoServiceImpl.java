package com.redisops.platform.operation.service.impl;

import com.redisops.platform.operation.mapper.TaskNoSequenceMapper;
import com.redisops.platform.operation.service.TaskNoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 生成 TASKyyyyMMdd0001 格式的业务任务编号。
 */
@Service
public class TaskNoServiceImpl implements TaskNoService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final TaskNoSequenceMapper sequenceMapper;

    /** 注入任务流水数据访问组件。 */
    public TaskNoServiceImpl(TaskNoSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    /**
     * 原子生成当天的下一个任务编号。
     *
     * @return 业务任务编号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String nextTaskNo() {
        LocalDate today = LocalDate.now();
        sequenceMapper.allocateNext(today);
        int value = sequenceMapper.selectAllocatedValue();
        if (value > 9999) {
            throw new IllegalStateException("当天任务数量超过 9999，无法继续生成任务编号");
        }
        return "TASK" + today.format(DATE_FORMATTER) + String.format("%04d", value);
    }
}
