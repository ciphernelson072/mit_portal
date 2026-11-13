package com.sis;

import com.sis.entity.ChatMessage;
import com.sis.repository.ChatMessageRepository;
import com.sis.service.CampusSupportAssistantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampusSupportAssistantServiceTest {

    @Test
    void storesQuestionAndReturnsHelpfulAssistantResponse() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CampusSupportAssistantService service = new CampusSupportAssistantService(repository);

        ChatMessage response = service.respond(7L, "How do I check my grades?");

        ArgumentCaptor<ChatMessage> messages = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repository, times(2)).save(messages.capture());
        assertEquals("STUDENT", messages.getAllValues().get(0).getSender());
        assertEquals("ASSISTANT", response.getSender());
        assertEquals(7L, response.getStudentId());
    }
}
