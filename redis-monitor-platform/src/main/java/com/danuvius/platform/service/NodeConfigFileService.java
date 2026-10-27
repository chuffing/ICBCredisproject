package com.danuvius.platform.service;

import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.util.Sha256Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理平台服务器上的Redis和Sentinel节点配置文件。
 *
 * <p>current目录保存已经正式使用过的配置，staging目录保存人工准备的
 * 下一次服务器切换配置。当前正式纳管的服务器由数据库决定。</p>
 */
@Service
public class NodeConfigFileService {

    private static final String REDIS_PORT = "6379";
    private static final String CURRENT_DIRECTORY_NAME = "current";
    private static final String STAGING_DIRECTORY_NAME = "staging";

    private final Path currentDirectory;
    private final Path stagingDirectory;

    public NodeConfigFileService(
            @Value("${platform.node-config.config-dir}")
            String configDirectory) {

        Path rootDirectory = Paths.get(configDirectory)
                .toAbsolutePath()
                .normalize();
        this.currentDirectory = rootDirectory
                .resolve(CURRENT_DIRECTORY_NAME)
                .normalize();
        this.stagingDirectory = rootDirectory
                .resolve(STAGING_DIRECTORY_NAME)
                .normalize();
    }

    /**
     * 读取current目录中指定服务器的Redis配置文件。
     *
     * @param hostIp 服务器IP
     * @return Redis配置文件
     */
    public File getRedisConfigFile(String hostIp) {
        return getConfigFile(
                currentDirectory,
                hostIp,
                ServerSwitchConstants.REDIS_CONFIG_FILE_NAME,
                "Redis"
        );
    }

    /**
     * 读取current目录中指定服务器的Sentinel配置文件。
     *
     * @param hostIp 服务器IP
     * @return Sentinel配置文件
     */
    public File getSentinelConfigFile(String hostIp) {
        return getConfigFile(
                currentDirectory,
                hostIp,
                ServerSwitchConstants.SENTINEL_CONFIG_FILE_NAME,
                "Sentinel"
        );
    }

    /**
     * 读取staging目录中指定服务器的Redis配置文件。
     *
     * @param hostIp 服务器IP
     * @return Redis配置文件
     */
    public File getStagingRedisConfigFile(String hostIp) {
        return getConfigFile(
                stagingDirectory,
                hostIp,
                ServerSwitchConstants.REDIS_CONFIG_FILE_NAME,
                "Redis"
        );
    }

    /**
     * 读取staging目录中指定服务器的Sentinel配置文件。
     *
     * @param hostIp 服务器IP
     * @return Sentinel配置文件
     */
    public File getStagingSentinelConfigFile(String hostIp) {
        return getConfigFile(
                stagingDirectory,
                hostIp,
                ServerSwitchConstants.SENTINEL_CONFIG_FILE_NAME,
                "Sentinel"
        );
    }

    /**
     * 计算配置文件的SHA-256摘要。
     *
     * @param configFile 配置文件
     * @param configName 配置文件说明
     * @return SHA-256摘要
     */
    public String calculateSha256(File configFile, String configName) {
        try {
            return Sha256Util.calculate(configFile.toPath());
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "读取" + configName + "配置文件失败："
                            + exception.getMessage()
            );
        }
    }


    /**
     * 校验staging目录中三台目标服务器的六份配置文件。
     */
    public void validateStagingConfigContent(
            String masterHostIp,
            List<String> replicaHostIps) {

        // 1. 主节点的Redis配置不能包含生效的主从复制指令。
        validateMasterRedisConfig(
                masterHostIp,
                getConfigFile(
                        stagingDirectory,
                        masterHostIp,
                        ServerSwitchConstants.REDIS_CONFIG_FILE_NAME,
                        "Redis"
                )
        );

        // 2. 两个从节点的Redis配置必须指向本次选择的主节点。
        for (String replicaHostIp : replicaHostIps) {
            validateReplicaRedisConfig(
                    replicaHostIp,
                    masterHostIp,
                    getConfigFile(
                            stagingDirectory,
                            replicaHostIp,
                            ServerSwitchConstants.REDIS_CONFIG_FILE_NAME,
                            "Redis"
                    )
            );
        }

        // 3. 三份Sentinel配置必须使用相同名称监控本次主节点。
        List<String> hostIps = new ArrayList<>();
        hostIps.add(masterHostIp);
        hostIps.addAll(replicaHostIps);

        String sentinelMasterName = null;
        for (String hostIp : hostIps) {
            String currentMasterName = readSentinelMasterName(
                    hostIp,
                    masterHostIp,
                    getConfigFile(
                            stagingDirectory,
                            hostIp,
                            ServerSwitchConstants.SENTINEL_CONFIG_FILE_NAME,
                            "Sentinel"
                    )
            );

            if (sentinelMasterName == null) {
                sentinelMasterName = currentMasterName;
            } else if (!sentinelMasterName.equals(currentMasterName)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "三份Sentinel配置监控的主节点名称不一致"
                );
            }
        }
    }

    /**
     * 将三台目标服务器的配置从staging复制到current。
     *
     * <p>复制操作允许重复执行。数据库事务失败时已经复制的文件可以保留，
     * 因为服务器是否正式纳管只由数据库中的纳管状态决定。</p>
     *
     * @param hostIps 三台目标服务器IP
     */
    public void copyStagingConfigsToCurrent(List<String> hostIps) {
        try {
            Files.createDirectories(currentDirectory);

            for (String hostIp : hostIps) {
                // 先确认staging中的两份源文件存在并且可读。
                File redisConfigFile = getStagingRedisConfigFile(hostIp);
                File sentinelConfigFile = getStagingSentinelConfigFile(hostIp);

                Path targetHostDirectory = currentDirectory
                        .resolve(hostIp)
                        .normalize();
                if (!targetHostDirectory.startsWith(currentDirectory)) {
                    throw new BusinessException(
                            BusinessCode.PARAMS_ERROR.getCode(),
                            "服务器IP不正确"
                    );
                }

                Files.createDirectories(targetHostDirectory);
                Files.copy(
                        redisConfigFile.toPath(),
                        targetHostDirectory.resolve(
                                ServerSwitchConstants.REDIS_CONFIG_FILE_NAME
                        ),
                        StandardCopyOption.REPLACE_EXISTING
                );
                Files.copy(
                        sentinelConfigFile.toPath(),
                        targetHostDirectory.resolve(
                                ServerSwitchConstants
                                        .SENTINEL_CONFIG_FILE_NAME
                        ),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "复制正式配置文件失败：" + exception.getMessage()
            );
        }
    }


    /**
     * 校验目标主节点的Redis配置。
     */
    private void validateMasterRedisConfig(
            String hostIp,
            File redisConfigFile) {

        List<String> configLines = readConfigLines(
                redisConfigFile,
                "Redis"
        );
        for (String configLine : configLines) {
            String[] values = configLine.split("\\s+");
            if (isReplicaOfDirective(values[0])) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        hostIp
                                + "是目标主节点，redis.conf中不能配置replicaof或slaveof"
                );
            }
        }
    }

    /**
     * 校验目标从节点的Redis配置。
     */
    private void validateReplicaRedisConfig(
            String replicaHostIp,
            String masterHostIp,
            File redisConfigFile) {

        List<String> configLines = readConfigLines(
                redisConfigFile,
                "Redis"
        );
        boolean replicaOfFound = false;

        for (String configLine : configLines) {
            String[] values = configLine.split("\\s+");
            if (!isReplicaOfDirective(values[0])) {
                continue;
            }

            replicaOfFound = true;
            if (values.length < 3
                    || !masterHostIp.equals(values[1])
                    || !REDIS_PORT.equals(values[2])) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        replicaHostIp
                                + "服务器的redis.conf未指向目标主节点"
                );
            }
        }

        if (!replicaOfFound) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    replicaHostIp
                            + "服务器的redis.conf缺少replicaof配置"
            );
        }
    }

    /**
     * 读取并校验Sentinel配置中的主节点名称。
     */
    private String readSentinelMasterName(
            String hostIp,
            String masterHostIp,
            File sentinelConfigFile) {

        List<String> configLines = readConfigLines(
                sentinelConfigFile,
                "Sentinel"
        );
        String masterName = null;

        for (String configLine : configLines) {
            String[] values = configLine.split("\\s+");
            if (values.length < 2
                    || !"sentinel".equalsIgnoreCase(values[0])
                    || !"monitor".equalsIgnoreCase(values[1])) {
                continue;
            }

            if (values.length < 6
                    || !masterHostIp.equals(values[3])
                    || !REDIS_PORT.equals(values[4])) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        hostIp
                                + "服务器的sentinel.conf未监控目标主节点"
                );
            }

            if (masterName != null && !masterName.equals(values[2])) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        hostIp
                                + "服务器的sentinel.conf存在不同的主节点名称"
                );
            }
            masterName = values[2];
        }

        if (masterName == null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    hostIp
                            + "服务器的sentinel.conf缺少sentinel monitor配置"
            );
        }
        return masterName;
    }

    /**
     * 读取配置文件中的有效配置行，忽略空行和注释行。
     */
    private List<String> readConfigLines(
            File configFile,
            String configName) {

        try {
            List<String> effectiveLines = new ArrayList<>();
            List<String> fileLines = Files.readAllLines(
                    configFile.toPath()
            );

            for (String fileLine : fileLines) {
                String configLine = fileLine.trim();
                if (!configLine.isEmpty()
                        && !configLine.startsWith("#")) {
                    effectiveLines.add(configLine);
                }
            }
            return effectiveLines;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "读取" + configName + "配置文件内容失败："
                            + exception.getMessage()
            );
        }
    }

    /**
     * 判断配置项是否为Redis主从复制指令。
     */
    private boolean isReplicaOfDirective(String directive) {
        return "replicaof".equalsIgnoreCase(directive)
                || "slaveof".equalsIgnoreCase(directive);
    }

    /**
     * 读取并检查指定服务器的配置文件。
     */
    private File getConfigFile(
            Path sourceDirectory,
            String hostIp,
            String fileName,
            String configName) {

        try {
            Path hostDirectory = sourceDirectory
                    .resolve(hostIp)
                    .normalize();
            Path configFile = hostDirectory
                    .resolve(fileName)
                    .normalize();

            if (!configFile.startsWith(sourceDirectory)
                    || !Files.isRegularFile(configFile)
                    || !Files.isReadable(configFile)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        hostIp + "服务器的" + configName
                                + "配置文件不存在或不可读"
                );
            }

            long fileSize = Files.size(configFile);
            if (fileSize <= 0
                    || fileSize > ServerSwitchConstants
                    .MAX_CONFIG_FILE_SIZE_BYTES) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        hostIp + "服务器的" + configName
                                + "配置文件为空或超过1 MiB"
                );
            }
            return configFile.toFile();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "读取" + hostIp + "服务器的" + configName
                            + "配置文件失败：" + exception.getMessage()
            );
        }
    }
}
