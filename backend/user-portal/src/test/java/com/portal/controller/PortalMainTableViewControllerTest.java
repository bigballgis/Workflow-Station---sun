package com.portal.controller;

import com.platform.common.exception.GlobalExceptionHandler;
import com.portal.security.CurrentUserIdArgumentResolver;
import com.portal.service.PortalMainTableViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PortalMainTableViewControllerTest {
    private final PortalMainTableViewService service = mock(PortalMainTableViewService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PortalMainTableViewController(service))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest @ValueSource(longs={1,2})
    void importRouteDoesNotExistAndCannotReachAWriteService(long view) throws Exception {
        var file = new MockMultipartFile("file","view.csv","text/csv","Subject\nValue\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/main-table-views/"+view+"/import").file(file).header("X-User-Id","reader"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test void exportStillReturnsCsvThroughTheAuthorizedViewService() throws Exception {
        byte[] csv="processInstanceId\nrequest\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.exportViewCsv(eq("reader"),eq(1L),eq(10000),any())).thenReturn(csv);
        mvc.perform(post("/main-table-views/1/export").header("X-User-Id","reader")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"page\":0,\"size\":20}"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv"))
                .andExpect(content().bytes(csv));
        verify(service).exportViewCsv(eq("reader"),eq(1L),eq(10000),any());
    }
}
