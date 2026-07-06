package com.example.kametud_catalog.client;

import java.util.UUID;

public interface IdentityClient {

    StudentStatusResponse getStudentStatus(UUID studentId);

    StudentProfileSummary getStudentProfile(UUID studentId);
}
