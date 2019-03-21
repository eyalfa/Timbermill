package com.datorama.timbermill.unit;

import com.google.gson.Gson;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;

import javax.validation.constraints.NotNull;

import static com.datorama.timbermill.common.Constants.TYPE;

public class SuccessEvent extends Event {
    public SuccessEvent(String taskId, @NotNull LogParams logParams) {
        super(taskId, null, logParams, null);
    }

    @Override
    public UpdateRequest getUpdateRequest(String index, Gson gson) {
        UpdateRequest updateRequest = new UpdateRequest(index, TYPE, getTaskId());
        Task task = new Task(this, null, time, Task.TaskStatus.SUCCESS);
        updateRequest.upsert(gson.toJson(task), XContentType.JSON);
        updateRequest.doc(gson.toJson(task), XContentType.JSON);
        return updateRequest;
    }
}
