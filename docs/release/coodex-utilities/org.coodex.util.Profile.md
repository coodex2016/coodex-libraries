# Profile

profile是一个key-value的模型。

## 主要接口

- getInt(String)/ getInt(String, int)

    从profile中获取指定key的整型值

- getBool(String)/ getBool(String, boolean)

    从profile中获取指定key的布尔值

- getLong(String)/ getLong(String, long)

    从profile中获取指定key的长整型值

- getString(String)/ getString(String, String)

    从profile中获取指定key的字符串值

- getStrList(String)/ getStrList(String, String)/ getStrList(String, String, String[])

    从profile中获取指定key的字符串数组

## usage

```java
    // 根据指定资源名获取Profile实例，未指定类型时，将自动检索
    Profile profile = Profile.get("profileName");

    // 多项资源合并为一个Profile，取值时，越靠前的优先级越高
    Profile profile = Profile.get("a", "b", "c");
```

Profile默认支持.properties，如果你的工程中引入了snakeyaml，则profile支持.yml|yaml文件

## 替换符

当某个键值引用其他资源的键值时，可使用替换符，格式为: `${refResource:key}`

## 系统变量

- `org.coodex.util.Profile.reloadInterval`, 重读间隔，单位为秒，默认不重读
- `Profile.reloadInterval`，同上，即将作废

## 扩展

`Profile`支持扩展你自己的键值对格式资源，实现一个`org.coodex.util.ProfileProvider`放到SPI即可。
