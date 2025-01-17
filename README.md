# ![BANNER](https://github.com/alibaba/jvm-sandbox/wiki/img/BANNER.png)

[![Build Status](https://travis-ci.org/alibaba/jvm-sandbox.svg?branch=master)](https://travis-ci.org/alibaba/jvm-sandbox)
[![codecov](https://codecov.io/gh/alibaba/jvm-sandbox/branch/master/graph/badge.svg)](https://codecov.io/gh/alibaba/jvm-sandbox)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/alibaba/jvm-sandbox.svg)](http://isitmaintained.com/project/alibaba/jvm-sandbox "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/alibaba/jvm-sandbox.svg)](http://isitmaintained.com/project/alibaba/jvm-sandbox "Percentage of issues still open")

> JVM沙箱容器，一种JVM的非侵入式运行期AOP解决方案<br/>
> Real - time non-invasive AOP framework container based on JVM

## 目标群体

- [BTRACE](https://github.com/btraceio/btrace)好强大，也曾技痒想做一个更便捷、更适合自己的问题定位工具，既可支持线上链路监控排查，也可支持单机版问题定位。
- 有时候突然一个问题反馈上来，需要入参才能完成定位，但恰恰没有任何日志，甚至出现在别人的代码里，好想开发一个工具可以根据需要动态添加日志，最好还能按照业务ID进行过滤。
- 系统间的异常模拟可以使用的工具很多，可是系统内的异常模拟怎么办，加开关或是用AOP在开发系统中实现，好想开发一个更优雅的异常模拟工具，既能模拟系统间的异常，又能模拟系统内的异常。
- 好想获取行调用链路数据，可以用它识别场景、覆盖率统计等等，覆盖率统计工具不能原生支持，统计链路数据不准确。想自己开发一个工具获取行链路数据。
- 我想开发录制回放、故障模拟、动态日志、行链路获取等等工具，就算我开发完成了，这些工具底层实现原理相同，同时使用，要怎么消除这些工具之间的影响，怎么保证这些工具动态加载，怎么保证动态加载/卸载之后不会影响其他工具，怎么保证在工具有问题的时候，快速消除影响，代码还原

如果你有以上研发诉求，那么你就是JVM-SANDBOX(以下简称沙箱容器)的潜在客户。沙箱容器提供

1. 动态增强类你所指定的类，获取你想要的参数和行信息甚至改变方法执行
2. 动态可插拔容器框架

## 项目简介

**JVM-SANDBOX（沙箱）实现了一种在不重启、不侵入目标JVM应用的AOP解决方案。**

### 沙箱的特性

1. `无侵入`：目标应用无需重启也无需感知沙箱的存在
2. `类隔离`：沙箱以及沙箱的模块不会和目标应用的类相互干扰
3. `可插拔`：沙箱以及沙箱的模块可以随时加载和卸载，不会在目标应用留下痕迹
4. `多租户`：目标应用可以同时挂载不同租户下的沙箱并独立控制
5. `高兼容`：支持JDK[6,11]

### 沙箱常见应用场景

- 线上故障定位
- 线上系统流控
- 线上故障模拟
- 方法请求录制和结果回放
- 动态日志打印
- 安全信息监测和脱敏

*JVM-SANDBOX还能帮助你做很多很多，取决于你的脑洞有多大了。*

### 实时无侵入AOP框架

在常见的AOP框架实现方案中，有静态编织和动态编织两种。

1. **静态编织**：静态编织发生在字节码生成时根据一定框架的规则提前将AOP字节码插入到目标类和方法中，实现AOP；
2. **动态编织**：动态编织则允许在JVM运行过程中完成指定方法的AOP字节码增强.常见的动态编织方案大多采用重命名原有方法，再新建一个同签名的方法来做代理的工作模式来完成AOP的功能(常见的实现方案如CgLib)，但这种方式存在一些应用边界：
   - **侵入性**：对被代理的目标类需要进行侵入式改造。比如：在Spring中必须是托管于Spring容器中的Bean
   - **固化性**：目标代理方法在启动之后即固化，无法重新对一个已有方法进行AOP增强
 
要解决`无侵入`的特性需要AOP框架具备 **在运行时完成目标方法的增强和替换**。在JDK的规范中运行期重定义一个类必须准循以下原则
  1. 不允许新增、修改和删除成员变量
  2. 不允许新增和删除方法
  3. 不允许修改方法签名

JVM-SANDBOX属于基于Instrumentation的动态编织类的AOP框架，**通过精心构造了字节码增强逻辑，使得沙箱的模块能在不违反JDK约束情况下实现对目标应用方法的`无侵入`运行时AOP拦截**。

## 核心原理

### 事件驱动

在沙箱的世界观中，任何一个Java方法的调用都可以分解为`BEFORE`、`RETURN`和`THROWS`三个环节，由此在三个环节上引申出对应环节的事件探测和流程控制机制。

```java
// BEFORE
try {

   /*
    * do something...
    */

    // RETURN
    return;

} catch (Throwable cause) {
    // THROWS
}
```

基于`BEFORE`、`RETURN`和`THROWS`三个环节事件分离，沙箱的模块可以完成很多类AOP的操作。

1. 可以感知和改变方法调用的入参
2. 可以感知和改变方法调用返回值和抛出的异常
3. 可以改变方法执行的流程
    - 在方法体执行之前直接返回自定义结果对象，原有方法代码将不会被执行
    - 在方法体返回之前重新构造新的结果对象，甚至可以改变为抛出异常
    - 在方法体抛出异常之后重新抛出新的异常，甚至可以改变为正常返回

### 类隔离策略

沙箱通过自定义的SandboxClassLoader破坏了双亲委派的约定，实现了和目标应用的类隔离。所以不用担心加载沙箱会引起应用的类污染、冲突。各模块之间类通过ModuleJarClassLoader实现了各自的独立，达到模块之间、模块和沙箱之间、模块和应用之间互不干扰。

![jvm-sandbox-classloader](https://github.com/alibaba/jvm-sandbox/wiki/img/jvm-sandbox-classloader.png)

### 类增强策略

沙箱通过在BootstrapClassLoader中埋藏的Spy类完成目标类和沙箱内核的通讯

![jvm-sandbox-enhance-class](https://github.com/alibaba/jvm-sandbox/wiki/img/jvm-sandbox-enhance-class.jpg)

### 整体架构

![jvm-sandbox-architecture](https://github.com/alibaba/jvm-sandbox/wiki/img/jvm-sandbox-architecture.png)

### 核心流程
依照个人对源码的理解，核心流程可以划分为三个，**连接**、**增强**、**执行**；
![jvm-sandbox核心流程](https://github.com/mylittlecabin/jvm-sandbox/blob/master/pic/jvm-sandbox%E6%A0%B8%E5%BF%83%E6%B5%81%E7%A8%8B.jpg?raw=true)
#### 连接
1. 连接命令包含在sandbox.sh脚本attach_jvm方法中，范例如下：
```text
    /Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin/java -Xms128M -Xmx128M 
    -Xnoclassgc -ea -Xbootclasspath/a:/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/lib/tools.jar 
    -jar /Users/zoubin08/my-app/jvm-sandbox/target/sandbox/bin/../lib/sandbox-core.jar 
    42352 /Users/zoubin08/my-app/jvm-sandbox/target/sandbox/bin/../lib/sandbox-agent.jar 'home=/Users/zoubin08/my-app/jvm-sandbox/target/sandbox/bin/..;token=223955225629;server.ip=0.0.0.0;server.port=0;namespace=default'
```
2. 以sandbox-core为启动jar先attach到目标业务应用jvm，以sandbox-agent为agent jar；
3. agent采用jetty构建http服务（实现通过http Command来激活、卸载增强逻辑）；
4. agent加载所有业务自定义module（自定义处理逻辑）；
#### 增强
1. sandbox.sh脚本中通过http形式给目标业务jvm发送指令；命令范例：
```text
   #broken-clock-tinker是module id
   #repairCheckState 基于Command注解标志的一个用于织入监听器逻辑的自定义方法
   curl -N -s http://wpsfile-sh.ks3-cn-shanghai.ksyun.com:55239/sandbox/default/module/http/broken-clock-tinker/repairCheckState
```
2. 对匹配的类的方法进行增强处理（基于Instrumentation和asm技术），增强主要就是增加了对Spy类方法的调用；
3. 将自定义处理逻辑封装到监听器中；
#### 执行
1. 被增强的业务方法增加了对Spy类方法的调用，Spy类方法通过调用监听器，最终执行到module中定义的命令方法逻辑；

## 快速安装
- **下载并安装或自行打包**

  ```shell
  # 下载最新版本的JVM-SANDBOX，oss已到期，或者oss链接不可访问时，可选择自行打包
  wget https://ompc.oss-cn-hangzhou.aliyuncs.com/jvm-sandbox/release/sandbox-1.3.3-bin.zip

  # 解压
  unzip sandbox-1.3.3-bin.zip
  ```
  ```shell
  #自行打包
   cd bin
   ./sandbox-packages.sh
   #target路径下有多种构建件类型，选择一个合适的使用
   cd ../target
  ```


- **挂载目标应用**

  ```shell
  # 进入沙箱执行脚本
  cd sandbox/bin

  # 目标JVM进程33342
  ./sandbox.sh -p 33342
  ```

- **挂载成功后会提示**

  ```shell
  ./sandbox.sh -p 33342
             NAMESPACE : default
               VERSION : 1.2.0
                  MODE : ATTACH
           SERVER_ADDR : 0.0.0.0
           SERVER_PORT : 55756
        UNSAFE_SUPPORT : ENABLE
          SANDBOX_HOME : /Users/vlinux/opt/sandbox
     SYSTEM_MODULE_LIB : /Users/vlinux/opt/sandbox/module
       USER_MODULE_LIB : ~/.sandbox-module;
   SYSTEM_PROVIDER_LIB : /Users/vlinux/opt/sandbox/provider
    EVENT_POOL_SUPPORT : DISABLE
  ```

- **卸载沙箱**

  ```shell
  ./sandbox.sh -p 33342 -S
  jvm-sandbox[default] shutdown finished.
  ```
## 调试
- **配置调试环境**

    目的是实现在同一个idea窗口可以同时编辑和调试jvm-sandbox和目标工程；
1. 命令生成新工程
```text
    mvn archetype:generate -DgroupId=com.mycompany.app -DartifactId=my-app -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```
2. 将新工程packaging类型修改为pom
  
3. 将jvm-sandbox和目标工程都copy到新工程下，并将新工程配置为二者的parent；

- **idea调试脚本命令配置**

    参考如下：
![脚本调试命令](https://github.com/mylittlecabin/jvm-sandbox/blob/master/pic/%E8%84%9A%E6%9C%AC%E6%89%A7%E8%A1%8C%E6%8C%87%E4%BB%A4.png?raw=true)

## 项目构建

当你修改了sandbox的代码后，想打包成自己需要的发行版，可以执行以下命令

> 脚本执行目录默认为项目主目录，后续不在另外说明

```shell
cd bin
./sandbox-package.sh
```

命令执行成功后会在target目录下生成`sandbox-<版本号>-bin.zip`文件

### 构建注意事项

1. 必须用JDK1.8进行构建，工程自身和maven插件中使用了tools.jar
2. 必须在Linux/Mac/Unix下进行构建，有部分测试用例没有考虑好$USER_HOME的目录路径在windows下的特殊性，会导致测试用例跑不通过。

### 修改sandbox版本号

sandbox的版本号需要修改所有的pom文件以及`.//sandbox-core/src/main/resources/com/alibaba/jvm/sandbox/version`，这里有一个脚本方便执行

```shell
cd bin
./set-version.sh -s 1.4.0
```

脚本第一个参数是`[s|r]`
- **s** : SNAPSHOT版，会自动在版本号后边追加`-SNAPSHOT`
- **r** : 正式版

### 本地仓库安装api包

如果本次你修改了sandbox-api、sandbox-common-api、sandbox-module-starter等本应该发布到中央仓库的包，但你需要本地测试验证，可以执行以下命令

```shell
mvn clean install
```

以下四个包将会安装到本地manven仓库
- sandbox
- sandbox-api
- sandbox-common-api
- sandbox-module-starter
- sandbox-provider-api

## 项目背景

2014年[GREYS](https://github.com/oldmanpushcart/greys-anatomy)第一版正式发布，一路看着他从无到有，并不断优化强大，感慨羡慕之余，也在想GREYS是不是只能做问题定位。

2015年开始根据GREYS的底层代码完成了人生的第一个字节码增强工具——动态日志。之后又萌生了将其拆解成*录制回放*、*故障模拟*等工具的想法。扪心自问，我是想以一人一个团队的力量建立大而全的工具平台，还是做一个底层中台，让每一位技术人员都可以在它的基础上快速的实现业务功能。我选择了后者。

## 相关文档

- **[WIKI](https://github.com/alibaba/jvm-sandbox/wiki/Home)**
