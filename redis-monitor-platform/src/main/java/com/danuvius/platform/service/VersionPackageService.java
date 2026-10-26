package com.danuvius.platform.service;

import com.danuvius.platform.model.vo.VersionPackageItemVO;
import com.danuvius.platform.model.vo.VersionPackagePageVO;
import com.danuvius.platform.model.vo.VersionPackageUploadVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Redis版本安装包上传服务。
 */
public interface VersionPackageService {

    /**
     * 上传Redis源码安装包。
     *
     * @param uploadedBy 上传操作人
     * @param file Redis源码安装包
     * @return 上传结果
     */
    VersionPackageUploadVO upload(
            String uploadedBy,
            MultipartFile file
    );

    /**
     * 分页查询Redis版本安装包。
     *
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 安装包分页结果
     */
    VersionPackagePageVO listPackages(Integer pageNum, Integer pageSize);

    /**
     * 查询全部可用的Redis版本安装包。
     *
     * @return 可用安装包列表
     */
    List<VersionPackageItemVO> listEnabledPackages();

    /**
     * 启用Redis版本安装包。
     *
     * @param packageId 安装包记录编号
     * @return 更新后的安装包信息
     */
    VersionPackageItemVO enablePackage(Long packageId);

    /**
     * 禁用Redis版本安装包。
     *
     * @param packageId 安装包记录编号
     * @return 更新后的安装包信息
     */
    VersionPackageItemVO disablePackage(Long packageId);
}
