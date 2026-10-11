package com.redisops.platform.versionupgrade.controller;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.versionupgrade.exception.VersionPackageConflictException;
import com.redisops.platform.versionupgrade.model.VersionPackageData;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Redis 版本包上传和查询接口。
 */
@RestController
@RequestMapping("/api/version-packages")
@RequiredArgsConstructor
public class VersionPackageController {

    private final VersionPackageService versionPackageService;

    /**
     * 上传一个 Redis tar.gz 版本包。
     *
     * @param version 用户声明的 Redis 版本号
     * @param uploadedBy 上传人
     * @param file 原始 tar.gz 文件
     * @return 新记录或完全重复上传时的已有记录
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VersionPackageData>> upload(
            @RequestParam("version") String version,
            @RequestParam("uploadedBy") String uploadedBy,
            @RequestPart("file") MultipartFile file) {
        try {
            VersionPackageData data = versionPackageService.upload(version, uploadedBy, file);
            HttpStatus status = data.isReusedExisting() ? HttpStatus.OK : HttpStatus.CREATED;
            String message = data.isReusedExisting()
                    ? "相同版本包已经存在，本次未重复保存"
                    : "版本包已保存并通过平台校验";
            return ResponseEntity.status(status).body(ApiResponse.success(message, data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_VERSION_PACKAGE", e.getMessage()));
        } catch (VersionPackageConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("VERSION_PACKAGE_CONFLICT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("VERSION_PACKAGE_UNAVAILABLE", e.getMessage()));
        }
    }

    /** 查询平台已经保存的全部版本包。 */
    @GetMapping
    public ApiResponse<List<VersionPackageData>> list() {
        return ApiResponse.success("版本包查询成功", versionPackageService.listPackages());
    }
}
