# keycloak-services-social-dingding

--- 

> Login with DingTalk in Keycloak.
> Keycloak 钉钉登录插件。

## 本地开发

### 构建包 Build package

```shell
mvn clean install
mvn clean package -e -U
```

### 版本更新

当需要更新本项目的版本时，需要修改 pom.xml 中的版本号。或者使用如下命令，比如将版本号改为 0.5.14：

```shell
mvn versions:set -DnewVersion=0.5.14
```

## 在 Keycloak 中引入的两种方式

### 通过手动拷贝到 /providers 目录

这需要你从源码执行 mvn clean install，在 target目录找到相应的 jar 包，然后拷贝到 Keycloak 的 providers 目录下。
当然也可以不用从源码自行编译，而是直接下载我编译好的，地址是： https://github.com/Jeff-Tian/keycloak-services-social-dingding/packages/1982789

### 通过 pom 方式引入

在 Keycloak 项目中的 pom 中引入，然后在 Dockerfile 里编译整个项目，顺带会将该插件编译出来，并统一拷贝。可以参考 https://github.com/Jeff-Tian/keycloak-heroku/blob/master/pom.xml 以及 https://github.com/Jeff-Tian/keycloak-heroku/blob/master/Dockerfile 。然而，由于以上 package 发布到了 GitHub 的仓库，尽管可以手动下载，但是通过 pom 方式引入时却需要一个 token。不过我有计划将它发布到公开的 Maven 中央仓库，这样就不需要 token 了，敬请期待。

通过 pom 方式引入后，还需要在 META-INF/services 里列出对它的引用，参考 https://github.com/Jeff-Tian/keycloak-heroku/blob/master/src/main/resources/META-INF/services/org.keycloak.broker.social.SocialIdentityProviderFactory ：

```plain
org.keycloak.social.dingding.DingDingIdentityProviderFactory
```

## 💵 欢迎问我！

有任何相关问题，欢迎来知乎咨询：

<a href="https://www.zhihu.com/consult/people/1073548674713423872" target="blank"><img src="https://first-go-vercel.vercel.app/api/dynamicimage" alt="向我咨询"/></a>

