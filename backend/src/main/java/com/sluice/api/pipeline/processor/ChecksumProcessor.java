package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

@Component
public class ChecksumProcessor implements Processor {
    @Override
    public void process(ProcessingContext context) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(context.getFileBytes());
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String checksum = hexString.toString();
        
        context.getAttributes().put("checksum", checksum);
        System.out.println("Computed SHA-256 Checksum for Job " + context.getJob().getId() + ": " + checksum);
    }
}
