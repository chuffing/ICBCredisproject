package com.danuvius.platform.controller;

import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.vo.VersionPackageItemVO;
import com.danuvius.platform.model.vo.VersionPackageUploadVO;
import com.danuvius.platform.service.VersionPackageService;
import com.danuvius.platform.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Redis版本安装包上传接口。
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
        String normalizedUploadedBy = StringUtils.trimToNull(uploadedBy);
        if (normalizedUploadedBy == null) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "上传操作人不能为空"
            );
        }
        if (normalizedUploadedBy.length()
                > ValidationConstants.MAX_CREATED_BY_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "上传操作人不能超过"
                            + ValidationConstants.MAX_CREATED_BY_LENGTH
                            + "个字符"
            );
        }

        // 2. 交给业务层校验并保存安装包。
        VersionPackageUploadVO result = versionPackageService.upload(
                normalizedUploadedBy,
                file
        );

        // 3. 返回安装包上传结果。
        return ResultBean.success(result);
    }

    /**
     * 查询全部Redis版本安装包。
     *
     * @return 安装包列表
     */
    @GetMapping("/list")
    public ResultBean<List<VersionPackageItemVO>> list() {

        // 1. 查询全部安装包。
        List<VersionPackageItemVO> versionPackages =
                versionPackageService.listPackages();

        // 2. 返回安装包列表。
        return ResultBean.success(versionPackages);
    }
}
