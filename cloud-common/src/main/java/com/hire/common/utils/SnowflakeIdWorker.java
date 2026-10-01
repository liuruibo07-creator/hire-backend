package com.hire.common.utils;

/**
 * 雪花算法生成分布式唯一 ID
 * 线程安全，单机 QPS 约 400 万
 */
public class SnowflakeIdWorker {

    // ==================== 常量定义 ====================

    /**
     * 起始时间戳（2015-01-01 00:00:00）
     * 所有生成的 ID 都基于这个时间差值，节省位数
     */
    private final long twepoch = 1420041600000L;

    /**
     * 工作机器 ID 占用的位数：5 位
     * 最大值 = 2^5 - 1 = 31
     */
    private final long workerIdBits = 5L;

    /**
     * 数据中心 ID 占用的位数：5 位
     * 最大值 = 2^5 - 1 = 31
     */
    private final long datacenterIdBits = 5L;

    /**
     * 工作机器 ID 最大值：31
     * 计算：~(-1L << 5) = ~(1111...11100000) = 0000...00011111 = 31
     */
    private final long maxWorkerId = ~(-1L << workerIdBits);

    /**
     * 数据中心 ID 最大值：31
     */
    private final long maxDatacenterId = ~(-1L << datacenterIdBits);

    /**
     * 序列号占用的位数：12 位
     * 同一毫秒内最多生成 4096 个 ID
     */
    private final long sequenceBits = 12L;

    /**
     * 工作机器 ID 左移位数：12 位
     * 因为序列号占 12 位，所以 workerId 要左移 12 位
     */
    private final long workerIdShift = sequenceBits;  // = 12

    /**
     * 数据中心 ID 左移位数：17 位
     * = 序列号位数(12) + 工作机器 ID 位数(5)
     */
    private final long datacenterIdShift = sequenceBits + workerIdBits;  // = 17

    /**
     * 时间戳左移位数：22 位
     * = 序列号(12) + 工作机器 ID(5) + 数据中心 ID(5)
     */
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;  // = 22

    /**
     * 序列号掩码：4095（二进制 111111111111）
     * 用于序列号溢出时归零
     * 计算：~(-1L << 12) = 0000...0000111111111111 = 4095
     */
    private final long sequenceMask = ~(-1L << sequenceBits);  // = 4095

    // ==================== 实例变量 ====================

    /** 工作机器 ID（0~31） */
    private long workerId;

    /** 数据中心 ID（0~31） */
    private long datacenterId;

    /**
     * 毫秒内序列号（0~4095）
     * 同一毫秒内每生成一个 ID，sequence + 1
     */
    private long sequence = 0L;

    /**
     * 上次生成 ID 的时间戳
     * 初始值 -1 表示还没有生成过 ID
     */
    private long lastTimestamp = -1L;

    /**
     * 单例实例（workerId=1, datacenterId=1）
     * 实际项目中可通过配置注入不同的 workerId
     */
    private static final SnowflakeIdWorker INSTANCE = new SnowflakeIdWorker(1, 1);

    // ==================== 构造方法 ====================

    /**
     * 构造函数
     * @param workerId      工作机器 ID（0~31）
     * @param datacenterId  数据中心 ID（0~31）
     */
    public SnowflakeIdWorker(long workerId, long datacenterId) {
        // 校验 workerId 范围
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(
                    "工作机器ID不能大于" + maxWorkerId + "或小于0");
        }
        // 校验 datacenterId 范围
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(
                    "数据中心ID不能大于" + maxDatacenterId + "或小于0");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    // ==================== 公共方法 ====================

    /**
     * 获取单例实例
     */
    public static SnowflakeIdWorker getInstance() {
        return INSTANCE;
    }

    /**
     * 生成下一个唯一 ID（线程安全）
     * @return 64 位 Long 型 ID
     */
    public synchronized long nextId() {
        // 1. 获取当前时间戳（毫秒）
        long timestamp = System.currentTimeMillis();

        // 2. 时钟回拨检测：如果当前时间小于上次生成 ID 的时间，说明时钟回拨了
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                    String.format("时钟发生回拨，拒绝生成ID，回拨毫秒数：%d",
                            lastTimestamp - timestamp));
        }

        // 3. 同一毫秒内的处理
        if (lastTimestamp == timestamp) {
            // 序列号 + 1，然后与掩码做与运算（相当于模 4096）
            sequence = (sequence + 1) & sequenceMask;

            // 如果序列号溢出（达到 4096），需要等待下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号归零
            sequence = 0L;
        }

        // 4. 更新上次时间戳
        lastTimestamp = timestamp;

        // 5. 拼接 ID：时间戳左移 22 位 | 数据中心 ID 左移 17 位 | 工作机器 ID 左移 12 位 | 序列号
        return ((timestamp - twepoch) << timestampLeftShift)  // 时间戳部分
                | (datacenterId << datacenterIdShift)          // 数据中心 ID 部分
                | (workerId << workerIdShift)                  // 工作机器 ID 部分
                | sequence;                                    // 序列号部分
    }

    /**
     * 阻塞等待直到下一毫秒
     * @param lastTimestamp 上次生成 ID 的时间戳
     * @return 下一毫秒的时间戳
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        // 自旋等待，直到当前时间大于 lastTimestamp
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 便捷静态方法：直接获取下一个 ID
     * 使用默认的单例实例（workerId=1, datacenterId=1）
     */
    public static long nextSnowflakeId() {
        return INSTANCE.nextId();
    }
}