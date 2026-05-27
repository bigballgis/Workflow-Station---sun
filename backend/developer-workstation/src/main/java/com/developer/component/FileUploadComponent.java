package com.developer.component;

import com.developer.entity.UploadedFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Generic file upload component. */
public interface FileUploadComponent {

    Map<String, Object> upload(MultipartFile file) throws IOException;

    UploadedFile getFile(String storedName);

    void deleteFile(String storedName);
}
