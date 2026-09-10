package com.pedacinhodemaria.modules.order.controller;

import com.pedacinhodemaria.modules.order.dto.OrderResponse;
import com.pedacinhodemaria.modules.order.dto.UpdateOrderStatusRequest;
import com.pedacinhodemaria.modules.order.service.OrderQueryService;
import com.pedacinhodemaria.modules.order.service.UpdateOrderStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints consumidos pelo Kitchen Dashboard. Fase 3: exigem JWT válido
 * com role KITCHEN ou OWNER (ver SecurityConfig) — antes eram públicos por
 * decisão temporária da Fase 2A, que deixou de valer assim que o Dashboard
 * ganhou login (Fase 2B). Continuam num controller próprio, separado de
 * OrderController, porque a audiência é diferente (cozinha vs. cliente
 * final) — separa também o que evolui junto no futuro.
 */
@RestController
@RequestMapping("/api/v1/kitchen/orders")
@RequiredArgsConstructor
@Tag(name = "Kitchen", description = "Consulta e atualização de pedidos pela cozinha")
public class KitchenOrderController {

    private final OrderQueryService orderQueryService;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    @GetMapping
    @Operation(summary = "Lista pedidos ativos (RECEIVED/PREPARING/READY) com timerState já calculado")
    public ResponseEntity<List<OrderResponse>> getActiveOrders() {
        return ResponseEntity.ok(orderQueryService.getActiveOrders());
    }

    @PatchMapping("/{orderCode}/status")
    @Operation(summary = "Move um pedido entre colunas, validando a transição de estado")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable String orderCode,
                                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(updateOrderStatusUseCase.execute(orderCode, request.newStatus()));
    }
}