package com.restopilot.backend;

import com.restopilot.backend.core.controller.ReadinessController;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadinessTest {
    @Test void returnsOnlyStatusAndReleasesConnection() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        var result = new ReadinessController(source).ready();
        assertEquals(200, result.getStatusCode().value());
        assertEquals(java.util.Map.of("status", "UP"), result.getBody());
        verify(connection).close();
    }
    @Test void databaseFailureDoesNotLeakDetails() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("private connection details"));
        var result = new ReadinessController(source).ready();
        assertEquals(503, result.getStatusCode().value());
        assertEquals(java.util.Map.of("status", "DOWN"), result.getBody());
    }
}
