package com.acme.order.common.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.junit.jupiter.api.Assertions.*;

class JsonRequestConfigurationTest {
    record Price(long priceCent) { }
    @Test void integerMoneyDoesNotSilentlyTruncateDecimals() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new JsonRequestConfiguration().strictIntegers().customize(builder);
        var mapper = builder.build();
        assertEquals(9900, mapper.readValue("{\"priceCent\":9900}", Price.class).priceCent());
        assertThrows(JsonMappingException.class, () -> mapper.readValue("{\"priceCent\":99.5}", Price.class));
    }
}
