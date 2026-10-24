package com.emeralds.mandatelens.agentpulse.api;

import com.emeralds.mandatelens.agentguard.client.HttpAgentGuardClient;
import com.emeralds.mandatelens.agentpulse.operation.PendingOperationRepository;
import com.emeralds.mandatelens.agentpulse.orchestration.MerchantOperationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/merchant-operations")
public class MerchantOperationController {
    private final MerchantOperationService service;

    public MerchantOperationController(MerchantOperationService service) { this.service = service; }

    @PostMapping("/check")
    public MerchantOperationService.CheckResponse check(@Valid @RequestBody MerchantOperationRequests.Check request) {
        try { return service.check(request); }
        catch (HttpAgentGuardClient.AgentGuardUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (MerchantOperationService.OperationAlreadyExistsException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Existing operationId=" + exception.operationId() + ", status=" + exception.status());
        } catch (MerchantOperationService.OperationNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/{operationId}/result")
    public MerchantOperationService.ResultResponse result(@PathVariable String operationId,
                                                           @Valid @RequestBody MerchantOperationRequests.Result request) {
        try { return service.result(operationId, request); }
        catch (MerchantOperationService.OperationNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException | PendingOperationRepository.OperationStateException
                 | PendingOperationRepository.DuplicateCallbackConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }
}
