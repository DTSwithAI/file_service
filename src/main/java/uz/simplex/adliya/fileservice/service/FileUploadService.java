package uz.simplex.adliya.fileservice.service;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import uz.simplex.adliya.base.exception.ExceptionWithStatusCode;
import uz.simplex.adliya.fileservice.dto.FilePreviewResponse;
import uz.simplex.adliya.fileservice.dto.FileUploadResponse;
import uz.simplex.adliya.fileservice.entity.FileEntity;
import uz.simplex.adliya.fileservice.repos.FileRepository;

import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

@Service
public class FileUploadService {


    private final FileRepository fileRepository;

    public FileUploadService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Value("${file.server.url}")
    private String url;

    @Value("${file.server.port}")
    private String port;

    @Value("${file.server.username}")
    private String username;

    @Value("${file.server.password}")
    private String password;

    @Value("${file.server.directory}")
    private String directoryName;

    private String createSha256(String word) {

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] digest = md.digest(word.getBytes(StandardCharsets.UTF_8));

        return DatatypeConverter.printHexBinary(digest).toLowerCase();
    }

    public String uploadFile(MultipartFile file) {


        try {
            // Connect to FTP server
            FTPClient ftpClient = new FTPClient();
            ftpClient.connect(url, Integer.parseInt(port));
            ftpClient.login(username, password);

            // Specify the remote directory on the FTP server
            ftpClient.changeWorkingDirectory(directoryName);
            Date currentDate = new Date();

            // Create date format to extract year, month, and day
            SimpleDateFormat dateFormatYear = new SimpleDateFormat("yyyy");
            SimpleDateFormat dateFormatMonth = new SimpleDateFormat("MM");
            SimpleDateFormat dateFormatDay = new SimpleDateFormat("dd");

            // Extract year, month, and day
            String year = dateFormatYear.format(currentDate);
            String month = dateFormatMonth.format(currentDate);
            String day = dateFormatDay.format(currentDate);

            FileEntity entity = new FileEntity();
            String yearDirectory = directoryName + "/" + year;
            createRemoteDirectory(ftpClient, year);
            String monthDirectory = yearDirectory + "/" + month;
            createRemoteDirectory(ftpClient, month);

            String dayDirectory = monthDirectory + "/" + day;
            createRemoteDirectory(ftpClient, day);


            // Upload a file
            String originalName = file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            boolean success = ftpClient.storeFile(originalName, inputStream);
            inputStream.close();
            String previewUrl;
            String sha256Hash;
            if (success) {
                sha256Hash = createSha256(file.getOriginalFilename()+System.currentTimeMillis());

                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(url + "/api/file-service/v1/preview")
                        .port(50000)
                        .queryParam(sha256Hash);
                previewUrl = uriBuilder.toUriString();
                String originalFilename = file.getOriginalFilename();
                entity.setExtension(Objects.requireNonNull(originalFilename).substring(originalFilename.lastIndexOf(".") + 1));
                entity.setName(file.getName());
                entity.setFileSize(String.valueOf(file.getSize()));
                entity.setContentType(file.getContentType());
                entity.setPath(dayDirectory);
                entity.setSha256(sha256Hash);
                entity.setOriginalName(originalFilename);
                entity.setHashId(null);
                entity.setInnerUrl(previewUrl);
                fileRepository.save(entity);
            } else {
                throw new ExceptionWithStatusCode(400, "file.upload.failed");
            }

            // Disconnect from the FTP server
            ftpClient.logout();
            ftpClient.disconnect();
            return previewUrl;

        } catch (IOException e) {
            throw new ExceptionWithStatusCode(400, "file.upload.failed");
        }


    }

    private static void createRemoteDirectory(FTPClient ftpClient, String remoteDirectory) throws IOException {
        if (!directoryExists(ftpClient, remoteDirectory)) {
            boolean b = ftpClient.makeDirectory(remoteDirectory);
            if (!b) {
                throw new ExceptionWithStatusCode(400,"file.upload.directory.create.failed");

            }
        }
        if (!ftpClient.changeWorkingDirectory(remoteDirectory)) {
            throw new ExceptionWithStatusCode(400,"file.upload.directory.create.failed");
        }

    }

    private static boolean directoryExists(FTPClient ftpClient, String directoryName) throws IOException {
        String[] directories = ftpClient.listNames();
        if (directories != null) {
            for (String dir : directories) {
                if (dir.equals(directoryName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public FileUploadResponse upload(MultipartFile file) {
        return new FileUploadResponse(uploadFile(file));

    }

    public ResponseEntity<byte[]> download(String code) {
        FileEntity fileEntity = fileRepository.findBySha256(code)
                .orElseThrow(() -> new ExceptionWithStatusCode(400, "file.not.found"));

        try {
            FTPClient ftpClient = new FTPClient();
            ftpClient.connect(url);
            ftpClient.login(username, password);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean success = ftpClient.retrieveFile(fileEntity.getPath()+"/"+fileEntity.getOriginalName(), outputStream);
            ftpClient.logout();
            ftpClient.disconnect();

            if (success) {
                byte[] fileData = outputStream.toByteArray();

                return  ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=" + new File(fileEntity.getPath()+"/"+fileEntity.getOriginalName()).getName())
                        .body(fileData);

            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public FilePreviewResponse preview(String code) {

        FileEntity fileEntity = fileRepository.findBySha256(code)
                .orElseThrow(() -> new ExceptionWithStatusCode(400, "file.not.found"));
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(url + "/api/file-service/v1/download")
                .port(50000)
                .queryParam(fileEntity.getSha256());

     return   new FilePreviewResponse(
               uriBuilder.toUriString(),
               fileEntity.getExtension(),
               fileEntity.getOriginalName(),
               fileEntity.getFileSize()
       );
    }
}
