package cn.pepedd.codesandbox.service.java.impl.old;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import cn.pepedd.codesandbox.model.ExecuteCodeRequest;
import cn.pepedd.codesandbox.model.ExecuteCodeResponse;
import cn.pepedd.codesandbox.model.ExecuteMessage;
import cn.pepedd.codesandbox.model.JudgeInfo;
import cn.pepedd.codesandbox.service.CodeSandbox;
import cn.pepedd.codesandbox.utils.ProcessUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandboxImpl implements CodeSandbox {
  // 转大写快捷键 CTRL + U
  // 这里将会生成在 target 下的 tmp/code 目录
  private static final String DEFAULT_CODE_PATH = "tmp/code";
  public static final String DEFAULT_CODE_NAME = "Main.java";
  // 最大超时时间
  private static final long TIME_OUT = 5000L;
  // Java安全管理器
  private static final String SECURITY_MANAGER_PATH = "classpath:/resources/security";

  private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

  // ** 使用字典树检测违禁词 ** //
  private static final List<String> blackList = Arrays.asList("Files", "exec");

  private static final WordTree WORD_TREE;

  static {
    // 初始化字典树
    WORD_TREE = new WordTree();
    WORD_TREE.addWords(blackList);
  }

  public static void main(String[] args) {
    CodeSandbox codeSandbox = new JavaNativeCodeSandboxImpl();
    ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
    executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
    // 读取 resources/code 下的 Main.java
    String code = ResourceUtil.readStr("code/Main.java", StandardCharsets.UTF_8);
    executeCodeRequest.setCode(code);
    executeCodeRequest.setLanguage("java");
    codeSandbox.executeCode(executeCodeRequest);
  }

  @Override
  public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
    List<String> inputList = executeCodeRequest.getInputList();
    String code = executeCodeRequest.getCode();
    String language = executeCodeRequest.getLanguage();

    //  校验代码中是否包含黑名单中的命令
    FoundWord foundWord = WORD_TREE.matchWord(code);
    if (foundWord != null) {
        System.out.println("包含禁止词：" + foundWord.getFoundWord());
        return null;
    }

    // 1. 创建程序文件
    File file = createFile(DEFAULT_CODE_NAME, code);
    // 2. 编译代码
    if (file == null) {
      return null;
    }
    String compileCmd = String.format("javac -encoding utf-8 %s", file.getAbsoluteFile());
    try {
      Process process = Runtime.getRuntime().exec(compileCmd);
      ProcessUtil.execute(process, "编译");
    } catch (Exception e) {
      return getErrResponse(e);
    }
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
        return getErrResponse(e);
      }
    }
    // 4. 收集结果
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

    // 5. 文件清理，临时文件放在在 target 目录下，使用 mvn clean 命令即可清除

    // 6. 错误处理 使用getErrResponse或者全局异常处理
    System.out.println(executeCodeResponse);
    return executeCodeResponse;
  }

  /**
   * 当沙盒运行出错时，返回错误信息
   *
   * @return
   */
  private ExecuteCodeResponse getErrResponse(Exception e) {
    ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
    executeCodeResponse.setOutputList(new ArrayList<>());
    executeCodeResponse.setMessage(e.getMessage());
    executeCodeResponse.setStatus(2);
    executeCodeResponse.setJudgeInfo(new JudgeInfo());
    return executeCodeResponse;
  }

  /**
   * 用户代码隔离存放
   * @param fileName
   * @param content
   * @return
   */
  private File createFile(String fileName, String content) {
    Path path = Paths.get(DEFAULT_CODE_PATH + File.separator + UUID.randomUUID() + File.separator + fileName);
    try {
      return FileUtil.writeString(content, path.toString(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
