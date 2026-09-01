package com.example.banking.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.banking.contracts.FlagKey;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MonitoringDataTest {
    @Test
    void everyMonitoringStreamCoversEveryRegisteredFlag() {
        Map<String, Object> snapshot = MonitoringData.snapshot("mock");
        Set<String> registeredKeys =
                Arrays.stream(FlagKey.values()).map(FlagKey::key).collect(Collectors.toSet());

        for (String stream :
                List.of("flags", "history", "releases", "errorLogs", "traces", "sessions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) snapshot.get(stream);
            assertEquals(registeredKeys.size(), rows.size(), stream);
            assertEquals(
                    registeredKeys,
                    rows.stream()
                            .map(row -> (String) row.get("flagKey"))
                            .collect(Collectors.toSet()),
                    stream);
        }
    }
}
