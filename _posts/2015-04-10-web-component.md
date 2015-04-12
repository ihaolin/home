# Web组件化开发

* 采用技术
	* 视图: [handlebars](http://handlebarsjs.com/): 一种静态模板，类似[Freemarker,]() [Velocity](), 但handlebars更轻量，同时支持[服务端](http://jknack.github.io/handlebars.java/)和[客户端](http://handlebarsjs.com/)。
	* 样式: css(or [sass](http://sass-lang.com/))。
	* 脚本: javascript(or [coffee](http://coffee-script.org/))。
	* js模块管理程序: 如[require.js](http://www.requirejs.cn/), [sea.js](http://seajs.org/docs/)等。
	* 前端构建工具: 如[Grunt](http://gruntjs.com/),[Gulp](https://github.com/gulpjs/gulp/blob/master/docs/API.md)等。
	
* 环境
     * [Spring MVC](http://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)容器内。

* 思路
	* 视图 = 组件1 + 组件2 + 组件3 + ...
	* 组件 = hbs + js + scss + [数据接口配置(可选)]	
 *  案例(开发一个用户信息管理页面)
 
 	* 视图(users.hbs)
 	
 		```handlebars
		{{#partial "realTitle"}}用户管理{{/partial}}
		{{#partial "realBody"}}
		    <!-- component标签将加载叫member/users的组件 -->
		    {{component "member/users"}}	
		{{/partial}}
		<!-- 使用index_layout布局 -->
		{{> index_layout}} 
		```
 	* 组件(member/users)
 	
	 	* hbs 
	 	
	 	```handlebars		
		<div class="users js-comp" data-module="Users">
  			...
		</div>
	 	```	 	
	 	* scss(上面hbs的样式)
		
		```css
		.users{
			...
		}
		```
		 	
	 	* js(绑定hbs的事件处理程序，这里定义一个叫<font color="red">**Users**</font>的模块，并且该模块依赖<font color="red">Pagination</font>模块)
	
		```javascript
		define("Users", ['Pagination'], function(page){
		    var self = {
		        constructor: function(){
		        	...   
		        },
		        ...
	         return self;
		});
		```
		
		* 配置
		
		```ruby
		  - path: "member/users"
      		service: "com.xxx.user.service.UserService:paging"
     		type: SPRING
		```		 		* 渲染过程
 		* 前端目录结构
 		
 		```bash
 		├── assets 		 # 静态文件目录
		├── components	 # 组件目录
		├── conf 		 # 配置
		└── views 		 # 视图
 		```
 		* 后端配置
 		
 		```python
 		# 前端文件跟目录
 		assetsHome=/path/to/assets
 		```
 		
 		```xml
		<!-- 在Spring MVC上下文中配置模板渲染引擎 -->
	 	<context:component-scan base-package="com.xxx.web.handlebars" />
	    <bean id="viewResolver" name="viewResolver" class="com.xxx.HandlebarsViewResolver">
	    	<property name="assetsHome" value="#{assetsHome}" />
	    </bean>
 		```
		* 当浏览器发起www.xxx.com/users请求时，mvc会首先匹配找到视图文件<font color="red">{assetsHome}/views/users.hbs</font>，users.hbs中<font color="red">component</font>标签会注入一个组件<font color="red">{assetsHome}/components/member/users/main.hbs</font>，然后根据数据接口配置，调用<font color="red">UserService.paging</font>方法获取数据，进行数据渲染，返回视图。
		
* 后端MVC模块可见
	- [git@git.bingex.com:ishansong-mvc.git]()
     
