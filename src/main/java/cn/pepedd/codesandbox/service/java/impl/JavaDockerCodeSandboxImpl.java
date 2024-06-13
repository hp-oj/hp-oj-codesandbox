package cn.pepedd.codesandbox.service.java.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.pepedd.codesandbox.model.ExecuteCodeRequest;
import cn.pepedd.codesandbox.model.ExecuteCodeResponse;
import cn.pepedd.codesandbox.model.ExecuteMessage;
import cn.pepedd.codesandbox.model.JudgeInfo;
import cn.pepedd.codesandbox.service.java.JavaCodeSandboxTemplate;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TODO
 *
 * @author pepedd864
 * @since 2024/6/12
 */
@Component
public class JavaDockerCodeSandboxImpl extends JavaCodeSandboxTemplate {
  private static final Boolean FIRST_INIT = true;
  private DockerClient dockerClient;
  private String containerId;

  public static void main(String[] args) {
    JavaDockerCodeSandboxImpl javaDockerCodeSandbox = new JavaDockerCodeSandboxImpl();
    ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
    executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
    String code = ResourceUtil.readStr(" code/Main.java", StandardCharsets.UTF_8);
    executeCodeRequest.setCode(code);
    executeCodeRequest.setLanguage("java");
    ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
    System.out.println(executeCodeResponse);
  }

  /**
   * 执行代码
   *
   * @param executeCodeRequest
   * @return
   */
  @Override
  public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
    return super.executeCode(executeCodeRequest);
  }

  private void createContainer() {
    // 3. 使用Docker执行代码
    // 3.1 创建容器
    DockerClient dockerClient = DockerClientBuilder.getInstance().build();
    this.dockerClient = dockerClient;
    // 3.2 拉取镜像
    String image = "openjdk:8-alpine";
    if (FIRST_INIT) {
      PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
      try {
        pullImageCmd
            .exec(new PullImageResultCallback() {
              @Override
              public void onNext(PullResponseItem item) {
                System.out.println("下载镜像：" + item.getStatus());
                super.onNext(item);
              }
            })
            .awaitCompletion();
      } catch (InterruptedException e) {
        System.out.println("拉取镜像异常");
        throw new RuntimeException(e);
      }
    }
    System.out.println("下载完成");
    // 3.3 创建容器
    CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
    HostConfig hostConfig = new HostConfig();
    hostConfig.withMemory(100 * 1000 * 1000L);
    hostConfig.withMemorySwap(0L);
    hostConfig.withCpuCount(1L);
    // hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
    hostConfig.setBinds(new Bind(super.file.getParent(), new Volume("/app"))); // 将本地编译文件的路由映射到/app目录下
    CreateContainerResponse createContainerResponse = containerCmd
        .withHostConfig(hostConfig)
        .withNetworkDisabled(true)
        .withReadonlyRootfs(true)
        .withAttachStdin(true)
        .withAttachStderr(true)
        .withAttachStdout(true)
        .withTty(true)
        .exec();
    String containerId = createContainerResponse.getId();
    this.containerId = containerId;
    // 3.4 启动容器
    dockerClient.startContainerCmd(containerId).exec();
  }

  /**
   * 2. 编译文件 Docker 重写 TODO
   *
   * @param file
   * @return
   */
  @Override
  public ExecuteMessage compileFile(File file) {
    // 创建容器
    createContainer();
    // 3.5 编译文件
    ExecuteMessage executeMessage = new ExecuteMessage();
    ExecCreateCmdResponse compileFileResponse = dockerClient.execCreateCmd(containerId)
        .withCmd("javac", "-encoding", "utf-8", "/app/Main.java")
        .withAttachStderr(true)
        .withAttachStdin(true)
        .withAttachStdout(true)
        .exec();

    System.out.println("编译文件命令：" + compileFileResponse);
    String execCompileId = compileFileResponse.getId();
    try {
      dockerClient.execStartCmd(execCompileId).exec(new ExecStartResultCallback() {
        @Override
        public void onNext(Frame item) {
          String message = new String(item.getPayload(), StandardCharsets.UTF_8);
          StreamType streamType = item.getStreamType();
          if (streamType == StreamType.STDERR) {
            System.out.println("输出错误结果：" + message);
            executeMessage.setErrorMessage(message);
          } else {
            System.out.println("输出结果：" + message);
            executeMessage.setMessage(message);
          }
          super.onNext(item);
        }
      }).awaitCompletion();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return executeMessage;
  }

  /**
   * 3. 执行代码 Docker 重写 TODO
   *
   * @param file
   * @param code
   * @param inputList
   * @return
   */
  @Override
  public List<ExecuteMessage> runFile(File file, String code, List<String> inputList) {
    // 3.6 执行命令获得结果
    List<ExecuteMessage> executeMessageList = new ArrayList<>();
    for (String input : inputList) {
      StopWatch stopWatch = new StopWatch();
      String[] inputArgsArray = input.split(" ");
      // String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", file.getParent(), input);
      String[] runCmd = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
      ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
          .withCmd(runCmd)
          .withAttachStderr(true)
          .withAttachStdin(true)
          .withAttachStdout(true)
          .exec();
      System.out.println("创建执行命令：" + execCreateCmdResponse);

      ExecuteMessage executeMessage = new ExecuteMessage();
      final String[] message = {null};
      final String[] errorMessage = {null};
      long time = 0L;
      // 判断是否超时
      final boolean[] timeout = {true};
      String execId = execCreateCmdResponse.getId();
      final long[] maxMemory = {0L};

      // 获取占用的内存
      StatsCmd statsCmd = dockerClient.statsCmd(containerId);
      ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
        @Override
        public void onStart(Closeable closeable) {

        }

        @Override
        public void onNext(Statistics statistics) {
          System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
          maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
        }

        @Override
        public void onError(Throwable throwable) {

        }

        @Override
        public void onComplete() {

        }

        @Override
        public void close() throws IOException {

        }
      });
      statsCmd.exec(statisticsResultCallback);
      try {
        stopWatch.start();
        dockerClient.execStartCmd(execId)
            .exec(new ExecStartResultCallback() {
              @Override
              public void onComplete() {
                // 如果执行完成，则表示没超时
                timeout[0] = false;
                System.out.println("完成");
                super.onComplete();
              }

              @Override
              public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                if (StreamType.STDERR.equals(streamType)) {
                  errorMessage[0] = new String(frame.getPayload());
                  System.out.println("输出错误结果：" + errorMessage[0]);
                } else {
                  message[0] = new String(frame.getPayload());
                  System.out.println("输出结果：" + message[0]);
                }
                super.onNext(frame);
              }
            })
            // TODO 使用这种方式会导致新开线程，而获取不到运行的结果
//            .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
            .awaitCompletion();
        stopWatch.stop();
        time = stopWatch.getLastTaskTimeMillis();
        statsCmd.close();
      } catch (InterruptedException e) {
        System.out.println("程序执行异常");
        throw new RuntimeException(e);
      }
      executeMessage.setMessage(message[0]);
      executeMessage.setErrorMessage(errorMessage[0]);
      executeMessage.setTime(time);
      executeMessage.setMemory(maxMemory[0]);
      executeMessageList.add(executeMessage);
    }
    System.out.println("输出结果" + executeMessageList);
    return executeMessageList;
  }

  /**
   * 4. 收集整理输出结果 Docker 重写 TODO
   *
   * @param executeMessageList
   * @return
   */
  @Override
  public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
    // 4. 收集结果
    ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
    List<String> outputList = new ArrayList<>();
    // 计算时间最大值
    long maxTime = 0;
    long maxMemory = 0;
    for (ExecuteMessage executeMessage : executeMessageList) {
      String errorMessage = executeMessage.getErrorMessage();
      if (StrUtil.isNotBlank(errorMessage)) {
        executeCodeResponse.setMessage(errorMessage);
        // 存在错误
        executeCodeResponse.setStatus(3);
        break;
      }
      String output = executeMessage.getMessage();
      if (output != null) {
        output.trim();
        if (output.endsWith("\n")) {
          output.substring(0, output.length() - 2);
        }
      }
      outputList.add(output);
      Long time = executeMessage.getTime();
      Long memory = executeMessage.getMemory();
      if (time != null) {
        maxTime = Math.max(maxTime, time);
      }
      if (memory != null) {
        maxMemory = Math.max(maxMemory, memory);
      }
    }
    // 正常完成所有样例，设置为运行正常
    if (outputList.size() == executeMessageList.size()) {
      executeCodeResponse.setStatus(1);
    }
    executeCodeResponse.setOutputList(outputList);
    JudgeInfo judgeInfo = new JudgeInfo();

    judgeInfo.setTime(maxTime);
    judgeInfo.setMemory(maxMemory);
    executeCodeResponse.setJudgeInfo(judgeInfo);
    return executeCodeResponse;
  }

  /**
   * 5. 清理工作环境
   *
   * @param file
   * @return
   */
  @Override
  public Boolean clearWorkplace(File file) {
    dockerClient.stopContainerCmd(containerId).exec();
    dockerClient.removeContainerCmd(containerId).exec();
    try {
      dockerClient.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return super.clearWorkplace(file);
  }
}
