package com.civicsense.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Performs real infrastructure checks. A successful TCP connection is NOT
 * treated as integration: the database checks execute SQL, including a
 * PostGIS spatial function, and the storage check performs HeadBucket.
 */
@Service
public class InfrastructureHealthService {

    private final JdbcTemplate jdbc;
    private final S3Client s3;
    private final String bucket;

    public InfrastructureHealthService(
            JdbcTemplate jdbc,
            S3Client s3,
            @Value("${civicsense.storage.bucket}") String bucket) {
        this.jdbc = jdbc;
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Plain SQL round-trip against Supabase Postgres. */
    public boolean plainSqlWorks() {
        Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
        return result != null && result == 1;
    }

    /** Verifies PostGIS is installed and spatial functions actually execute. */
    public boolean postgisWorks() {
        String version = jdbc.queryForObject("SELECT postgis_version()", String.class);
        Boolean intersects = jdbc.queryForObject("""
                SELECT ST_Intersects(
                    ST_SetSRID(ST_MakePoint(0, 0), 4326),
                    ST_Buffer(ST_SetSRID(ST_MakePoint(0, 0), 4326), 0.01)
                )
                """, Boolean.class);
        return version != null && !version.isBlank() && Boolean.TRUE.equals(intersects);
    }

    /** Verifies the configured bucket exists through the S3-compatible API. */
    public boolean objectStorageWorks() {
        s3.headBucket(request -> request.bucket(bucket));
        return true;
    }
}
