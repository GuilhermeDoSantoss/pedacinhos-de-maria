package com.pedacinhodemaria.modules.auth.controller;

import com.pedacinhodemaria.config.SecurityConfig;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.security.JwtAuthenticationFilter;
import com.pedacinhodemaria.modules.auth.security.JwtService;
import com.pedacinhodemaria.modules.auth.service.ApproveUserUseCase;
import com.pedacinhodemaria.modules.auth.service.ListUsersUseCase;
import com.pedacinhodemaria.modules.auth.service.RejectUserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segundo teste de segurança HTTP real do projeto (o primeiro foi
 * KitchenOrderControllerSecurityTest, Fase 3). Mesmo padrão exato: carrega
 * o SecurityFilterChain de verdade via @WebMvcTest + @Import(SecurityConfig),
 * não confia em Mockito puro para validar authorizeHttpRequests.
 *
 * Não removi/alterei AdminUserControllerTest (o Mockito puro existente) —
 * este é um teste adicional, não um substituto.
 *
 * Mesma ressalva de sempre: não consegui executar isto neste sandbox
 * (Maven Central bloqueado). Se falhar, os candidatos mais prováveis de
 * causa são os mesmos documentados em KitchenOrderControllerSecurityTest.
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@ActiveProfiles("test")
class AdminUserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ListUsersUseCase listUsersUseCase;

    @MockBean
    private ApproveUserUseCase approveUserUseCase;

    @MockBean
    private RejectUserUseCase rejectUserUseCase;

    @Test
    void semJwtRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kitchenRecebe403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + jwtService.generateToken("cozinha@exemplo.com", UserRole.KITCHEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerRecebeAcessoPermitido() throws Exception {
        when(listUsersUseCase.execute(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + jwtService.generateToken("dona@exemplo.com", UserRole.OWNER)))
                .andExpect(status().isOk());
    }

    @Test
    void approveSemJwtRetorna401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveComKitchenRecebe403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/approve")
                        .header("Authorization", "Bearer " + jwtService.generateToken("cozinha@exemplo.com", UserRole.KITCHEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveComOwnerEPermitido() throws Exception {
        when(approveUserUseCase.execute("1")).thenReturn(null);

        mockMvc.perform(patch("/api/v1/admin/users/1/approve")
                        .header("Authorization", "Bearer " + jwtService.generateToken("dona@exemplo.com", UserRole.OWNER)))
                .andExpect(status().isOk());
    }
}