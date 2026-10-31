package de.medo.holonetbridge.commons.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolonetDiscordConfigTest {
    @ParameterizedTest
    @CsvSource({
            "1, holonetbridge-0000001",
            "123, holonetbridge-0000123",
            "123456789, holonetbridge-123456789"
    })
    void channelNamePadsToAtLeastSevenDigitsWithoutConsumingNumber(long number, String expectedName) {
        var config = new HolonetDiscordConfig();
        config.setNextTicketChannelSuffixNumber(number);

        assertThat(config.getNextTicketChannelName()).isEqualTo(expectedName);
        assertThat(config.getNextTicketChannelName()).isEqualTo(expectedName);
        assertThat(config.getNextTicketChannelSuffixNumber()).isEqualTo(number);
    }
}
