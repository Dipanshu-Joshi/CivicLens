package com.civicsense.testsupport;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only probe for the department-scope guard. Production endpoints never
 * accept a department id from the client; this exists solely to prove that a
 * verified admin cannot read another department's resource.
 */
@RestController
@RequestMapping("/department")
@Profile("test")
public class ScopeProbeController {

    @GetMapping("/scope-probe/{departmentId}")
    @PreAuthorize("@departmentGuard.ownsDepartment(authentication, #departmentId)")
    String probe(@PathVariable UUID departmentId) {
        return "ok";
    }
}
