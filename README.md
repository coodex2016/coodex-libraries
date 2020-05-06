# coodex libraries

coodex的一些通用库，从concrete项目中分离成独立项目



## change log

### 2020-05-06

- 增加`org.coodex.id.IDGenerator`，可以指定系统的`IDGeneratorService`来提供一致化的String id
  - 默认使用`org.coodex.id.SnowflakeIdGeneratorService`,参数
    - `snowflake.machineId`，机器号，[0-1023]，默认 -1，使用以下两个参数
    - `snowflake.workerId`，工作机号，[0-31]，默认 -1
    - `snowflake.dataCenterId`，数据中心号，[0-31]，默认 -1
    - 以上三个值均为-1时，使用`0`构建ID发生器
- 调整getResource和scanResource的方式，新增`org.coodex.util.ResourceScaner`来进行处理
  - 支持`spring boot maven plugin`打包的资源提取
  - 除`classpath`外，可以使用系统参数`coodex.resource.path`来指定文件系统中的资源路径，相对路径使用系统变量`user.dir`，使用路径分隔符隔开，方便打成单一jar包时使用非包内资源
    - 例如
      - linux: -Dcoodex.resource.path=../config:/etc/myApp/config
      - windows: -Dcoodex.resource.path=..\config;c:\etc\myApp\config
  - `org.coodex.util.Common.getResource`接口也支持上述参数
    
### 2020-04-07

- 自[concrete](https://github.com/coodex2016/concrete.coodex.org)([文档](https://concrete.coodex.org))项目分离出来单独立项
- 初始版本0.5.0-SNAPSHOT
- ~~弃用`gitbook`，使用[VuePress](https://vuepress.vuejs.org/)编写文档~~