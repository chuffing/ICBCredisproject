package com.danuvius.platform.service;

import com.danuvius.platform.model.vo.VersionPackageItemVO;
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
     * 查询全部Redis版本安装包。
     *
     * @return 安装包列表
     */
    List<VersionPackageItemVO> listPackages();
}
