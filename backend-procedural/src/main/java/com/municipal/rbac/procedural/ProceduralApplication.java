package com.municipal.rbac.procedural;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 一阶段面向功能后端入口；业务请求经 Controller → Service → JDBC Mapper，不依赖前端模拟 API。 */
@SpringBootApplication
public class ProceduralApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProceduralApplication.class, args);
  }
}
