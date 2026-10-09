package com.redisops.platform.operation.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 使用 MySQL 原子自增能力生成每日任务流水号。
 */
@Mapper
public interface TaskNoSequenceMapper {

    /** 为指定日期原子分配下一个流水号。 */
    @Insert("INSERT INTO task_no_sequence (biz_date, current_value) "
            + "VALUES (#{bizDate}, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1), "
            + "update_time = CURRENT_TIMESTAMP")
    int allocateNext(@Param("bizDate") LocalDate bizDate);

    /** 读取当前数据库连接刚刚分配的流水号。 */
    @Select("SELECT LAST_INSERT_ID()")
    int selectAllocatedValue();
}
