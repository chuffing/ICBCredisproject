package com.danuvius.platform.controller;

import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.model.vo.VersionPackageItemVO;
import com.danuvius.platform.model.vo.VersionPackagePageVO;
import com.danuvius.platform.model.vo.VersionPackageUploadVO;
import com.danuvius.platform.service.VersionPackageService;
import com.danuvius.platform.util.validate.VersionPackageRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Redis版本安装包管理接口。
 */
@RestController
@RequestMapping("/api/versionPackage")
@RequiredArgsConstructor
public class VersionPackageController {

    private final VersionPackageService versionPackageService;

    /**
     * 上传Redis源码安装包。
     *
     * @param uploadedBy 上传操作人
     * @param file Redis源码安装包
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ResultBean<VersionPackageUploadVO> upload(
            @RequestParam("uploadedBy") String uploadedBy,
            @RequestParam("file") MultipartFile file) {

        // 1. 校验并整理上传操作人。
        String normalizedUploadedBy =
                VersionPackageRequestValidator.validateUploadedBy(
                        uploadedBy
                );

        // 2. 交给业务层校验并保存安装包。
        VersionPackageUploadVO result = versionPackageService.upload(
                normalizedUploadedBy,
                file
        );

        // 3. 返回安装包上传结果。
        return ResultBean.success(result);
    }

    /**
     * 分页查询Redis版本安装包。
     *
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 安装包分页结果
     */
    @GetMapping("/list")
    public ResultBean<VersionPackagePageVO> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize) {

        // 1. 校验分页参数。
        VersionPackageRequestValidator.validatePage(pageNum, pageSize);

        // 2. 分页查询安装包。
        VersionPackagePageVO versionPackages =
                versionPackageService.listPackages(pageNum, pageSize);

        // 3. 返回安装包分页结果。
        return ResultBean.success(versionPackages);
    }

    /**
     * 查询全部可用的Redis版本安装包。
     *
     * @return 可用安装包列表
     */
    @GetMapping("/enabledList")
    public ResultBean<List<VersionPackageItemVO>> enabledList() {

        // 查询目标版本下拉框需要的全部可用安装包。
        return ResultBean.success(
                versionPackageService.listEnabledPackages()
        );
    }

    /**
     * 启用Redis版本安装包。
     *
     * @param packageId 安装包记录编号
     * @return 更新后的安装包信息
     */
    @PutMapping("/{packageId}/enable")
    public ResultBean<VersionPackageItemVO> enable(
            @PathVariable Long packageId) {

        // 1. 校验安装包记录编号。
        VersionPackageRequestValidator.validatePackageId(packageId);

        // 2. 启用安装包并返回最新状态。
        return ResultBean.success(
                versionPackageService.enablePackage(packageId)
        );
    }

    /**
     * 禁用Redis版本安装包。
     *
     * @param packageId 安装包记录编号
     * @return 更新后的安装包信息
     */
    @PutMapping("/{packageId}/disable")
    public ResultBean<VersionPackageItemVO> disable(
            @PathVariable Long packageId) {

        // 1. 校验安装包记录编号。
        VersionPackageRequestValidator.validatePackageId(packageId);

        // 2. 禁用安装包并返回最新状态。
        return ResultBean.success(
                versionPackageService.disablePackage(packageId)
        );
    }
}
