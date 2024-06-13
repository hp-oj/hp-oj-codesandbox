package cn.pepedd.codesandbox.service.java;

import cn.hutool.core.util.StrUtil;
import cn.pepedd.codesandbox.model.ExecuteCodeRequest;
import cn.pepedd.codesandbox.model.ExecuteCodeResponse;
import cn.pepedd.codesandbox.model.ExecuteMessage;
import cn.pepedd.codesandbox.model.JudgeInfo;
import cn.pepedd.codesandbox.service.CodeSandbox;
import cn.pepedd.codesandbox.utils.FileUtil;
import cn.pepedd.codesandbox.utils.ProcessUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO
 *
 * @author pepedd864
 * @since 2024/6/12
 */
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
  private static final String DEFAULT_CODE_PATH = "tmp/code";
  public static final String DEFAULT_CODE_NAME = "Main.java";
  // 最大超时时间
  private static final long TIME_OUT = 5000L;
  // Java安全管理器
  private static final String SECURITY_MANAGER_PATH = "classpath:/resources/security";

  private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
  public File file;

  /**
   * 执行代码
   *
   * @param executeCodeRequest
   * @return
   */
  @Override
  public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
    List<String> inputList = executeCodeRequest.getInputList();
    String code = executeCodeRequest.getCode();
    String language = executeCodeRequest.getLanguage();

    // 1. 创建程序文件
    File file = FileUtil.createFile(DEFAULT_CODE_NAME, code);
    this.file = file;
    // 2. 编译文件
    ExecuteMessage compileFileExecuteMessage = compileFile(file);
    System.out.println(compileFileExecuteMessage);
    // 3. 执行代码，得到输出结果
    List<ExecuteMessage> executeMessageList = runFile(file, code, inputList);
    // 4. 收集整理输出结果
    ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);
    // 5. 清理文件
    clearWorkplace(file);
    return outputResponse;
  }


  /**
   * 2. 编译文件 Docker 重写 TODO
   *
   * @param file
   * @return
   */
  public ExecuteMessage compileFile(File file) {
    if (file == null) {
      return null;
    }
    String compileCmd = String.format("javac -encoding utf-8 %s", file.getAbsoluteFile());
    try {
      Process process = Runtime.getRuntime().exec(compileCmd);
      ProcessUtil.execute(process, "编译");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  /**
   * 3. 执行代码 Docker 重写 TODO
   *
   * @param code
   * @param inputList
   * @return
   */
  public List<ExecuteMessage> runFile(File file, String code, List<String> inputList) {
    // 3. 执行代码 得到输出结果
    List<ExecuteMessage> executeMessageList = new ArrayList<>();
    for (String input : inputList) {
//      String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", file.getParent(), input);
      // 使用 Xmx256m参数限制jvm最大内存
      // 使用 Dfile.encoding 指定编码
      // 使用 Djava.security.manager 指定安全管理器
      String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", file.getParent(), SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, input);
      try {
        Process process = Runtime.getRuntime().exec(runCmd);
        // 超时控制
        new Thread(() -> {
          try {
            Thread.sleep(TIME_OUT);
            System.out.println("超时了，中断");
            process.destroy();
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        });
        ExecuteMessage executeMessage = ProcessUtil.execute(process, "执行");
        System.out.println(executeMessage);
        executeMessageList.add(executeMessage);
      } catch (Exception e) {
        throw new RuntimeException("执行错误", e);
      }
    }
    return null;
  }

  /**
   * 4. 收集整理输出结果 Docker 重写 TODO
   *
   * @param executeMessageList
   * @return
   */
  public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
    ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
    List<String> outputList = new ArrayList<>();
    // 计算时间最大值
    long maxTime = 0;
    for (ExecuteMessage executeMessage : executeMessageList) {
      String errorMessage = executeMessage.getErrorMessage();
      if (StrUtil.isNotBlank(errorMessage)) {
        executeCodeResponse.setMessage(errorMessage);
        // 存在错误
        executeCodeResponse.setStatus(3);
        break;
      }
      outputList.add(executeMessage.getMessage());
      Long time = executeMessage.getTime();
      if (time != null) {
        maxTime = Math.max(maxTime, time);
      }
    }
    // 正常完成所有样例，设置为运行正常
    if (outputList.size() == executeMessageList.size()) {
      executeCodeResponse.setStatus(1);
    }
    executeCodeResponse.setOutputList(outputList);
    JudgeInfo judgeInfo = new JudgeInfo();

    // 获取运行内存，这个实现比较麻烦
    judgeInfo.setMemory(null);

    judgeInfo.setTime(maxTime);
    executeCodeResponse.setJudgeInfo(judgeInfo);
    return executeCodeResponse;
  }

  /**
   * 5. 清理工作环境
   *
   * @param file
   * @return
   */

  public Boolean clearWorkplace(File file) {
    return null;
  }
}
