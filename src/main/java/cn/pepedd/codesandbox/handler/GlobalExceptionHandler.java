package cn.pepedd.codesandbox.handler;

import cn.pepedd.codesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * TODO
 *
 * @author pepedd864
 * @since 2024/6/13
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  /**
   * 处理所有不可知的异常
   */
  @ExceptionHandler
  public ExecuteCodeResponse exceptionHandler(Exception e) {
    e.printStackTrace();
    ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
    executeCodeResponse.setMessage("执行失败");
    return executeCodeResponse;
  }
}
