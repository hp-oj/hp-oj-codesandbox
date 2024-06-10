package cn.pepedd.codesandbox.utils;

import cn.pepedd.codesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 进程管理类
 */
public class ProcessUtil {
  public static ExecuteMessage execute(Process process, String opName) {
    ExecuteMessage executeMessage = new ExecuteMessage();
    StopWatch stopWatch = new StopWatch();
    try {
      stopWatch.start();
      int exitValue = process.waitFor();
      executeMessage.setExitValue(exitValue);
      // 正常退出
      if (exitValue == 0) {
        System.out.println(opName + "成功");
        // 获opName+取输出
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder compileOutStr = new StringBuilder();
        String outputLine = null;
        while ((outputLine = bufferedReader.readLine()) != null) {
          compileOutStr.append(outputLine);
        }
        executeMessage.setMessage(compileOutStr.toString());
      } else {
        System.out.println(opName + "错误" + exitValue);
        // 获opName+取输出
        /// 正常输出
        System.out.println(opName + "成功");
        // 获opName+取输出
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder compileOutStr = new StringBuilder();
        String outputLine = null;
        while ((outputLine = bufferedReader.readLine()) != null) {
          compileOutStr.append(outputLine);
        }
        executeMessage.setMessage(compileOutStr.toString());
        /// 错误输出
        BufferedReader errBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder errCompileOutStr = new StringBuilder();
        String errOutputLine = null;
        while ((errOutputLine = errBufferedReader.readLine()) != null) {
          errCompileOutStr.append(errOutputLine);
        }
        executeMessage.setErrorMessage(errCompileOutStr.toString());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    stopWatch.stop();
    executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
    return executeMessage;
  }
}
