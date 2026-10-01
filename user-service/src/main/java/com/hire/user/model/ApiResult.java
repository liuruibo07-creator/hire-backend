package com.hire.user.model;
import com.fasterxml.jackson.annotation.*;
import com.hire.common.Result;
/** Service-local adapter for the documented JSON contract. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResult<T> extends Result<T> {
    @Override @JsonIgnore public String getMsg() { return super.getMsg(); }
    @JsonProperty("message") public String getMessage() { return super.getMsg(); }
    @JsonProperty("message") public void setMessage(String message) { super.setMsg(message); }
    public static <T> ApiResult<T> of(int code, String message, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code); result.setMsg(message); result.setData(data);
        return result;
    }
    public static <T> ApiResult<T> ok(String message, T data) { return of(200, message, data); }
}
