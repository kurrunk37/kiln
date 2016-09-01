# kiln 使用说明

这是一个静态blog生成器，可以通过配置和内容生成网页。

## 配置文件

当前目录的 _config.clj

```
{
  :input "./_writer/"
  :output "./html/"
  :template "./template/" ;(非必选)
  :blog-name "测试"
  :blog-url "http://blog.kurrunk.com"
  :blog-description "我的博客"
}
```

### 模板

模板格式为mustache，文件名必须以`.mustache`结尾。共有三个模板文件:

* index.mustache
* tag.mustache
* article.mustache

### 内容+元数据

网页的主内容，以及配置信息。文件是以.md结尾，内容分为两个部分：元数据、正文。

元数据在文件顶部，用多行“KEY:VALUE”的形式存在。通过编辑元数据可以设置生成的网页的时间、作者、模板、广告等。

#### 元数据项

date : 时间 (可选,默认为文件编辑时间)  
title : 标题  

#### 正文

正文的格式是markdown，如果内容只有文字，只需要像平常一样编辑内容即可。需要注意的是段落间需要有一个空行。

## 新增文章的步骤

1. 编辑markdown文件
2. 运行kiln.jar文件
3. 同步到你的服务器上

通过以上步骤在本地生成网页文件，下一步就可以上传到服务器上了。
