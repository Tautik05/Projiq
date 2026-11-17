package projiq.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTaskCommentRequest {

    @NotBlank(message = "Comment content cannot be empty")
    @Size(min = 1, max = 2000, message = "Comment cannot exceed 2000 characters")
    private String content;

    public CreateTaskCommentRequest() {
    }

    public CreateTaskCommentRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
