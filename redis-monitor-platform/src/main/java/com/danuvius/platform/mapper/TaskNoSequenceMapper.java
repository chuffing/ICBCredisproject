package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.entity.TaskNoSequence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 运维任务编号流水Mapper。
 */
@Mapper
public interface TaskNoSequenceMapper extends BaseMapper<TaskNoSequence> {

    /**
     * 原子获取指定日期的下一个任务流水号。
     *
     * @param bizDate 业务日期
     * @return 下一个任务流水号
     */
    Integer nextValue(@Param("bizDate") LocalDate bizDate);
}
