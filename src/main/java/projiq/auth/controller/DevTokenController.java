package projiq.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projiq.auth.dto.DevEmailLink;
import projiq.auth.service.DevEmailService;
import projiq.common.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@Tag(name = "Development Helper", description = "Development-only endpoints for viewing verification and reset links")
public class DevTokenController {

    private final DevEmailService devEmailService;

    public DevTokenController(DevEmailService devEmailService) {
        this.devEmailService = devEmailService;
    }

    @GetMapping("/tokens")
    @Operation(summary = "List recent development email tokens and links")
    public ResponseEntity<ApiResponse<List<DevEmailLink>>> getRecentLinks() {
        return ResponseEntity.ok(ApiResponse.success(devEmailService.getDevLinks()));
    }

    @PostMapping("/tokens/clear")
    @Operation(summary = "Clear recent development links")
    public ResponseEntity<ApiResponse<Void>> clearLinks() {
        devEmailService.clearDevLinks();
        return ResponseEntity.ok(ApiResponse.success("Development links cleared", null));
    }
}
