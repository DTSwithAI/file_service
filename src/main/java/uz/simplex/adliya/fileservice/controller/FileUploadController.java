package uz.simplex.adliya.fileservice.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.simplex.adliya.fileservice.dto.FilePreviewResponse;
import uz.simplex.adliya.fileservice.dto.FileUploadResponse;
import uz.simplex.adliya.fileservice.service.FileUploadService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestMapping("/api/file-service/v1")
@RestController
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(path = "/upload",produces = MediaType.APPLICATION_JSON_VALUE)
    public FileUploadResponse upload(@RequestPart MultipartFile multipartFile) {
        return fileUploadService.upload(multipartFile);
    }
    @GetMapping("/preview")
    public FilePreviewResponse preview(@RequestParam String code) {
        return fileUploadService.preview(code);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String code
                                             ) {

         return fileUploadService.download(code);

    }

}
