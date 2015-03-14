# Introduction #

We assume that you succeed in compiling the source code of **QuiXProc Open**

## Simple sample ##
Suppose that you have the following very simple XPL `add-attribute.xpl`:
```
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="1.0">
  <p:identity>
    <p:input port="source">
      <p:inline>
        <root/>
      </p:inline>
    </p:input>
  </p:identity>
  <p:add-attribute match="root" attribute-name="foo" attribute-value="bar"/>
  <p:store href="root-with-attribute.xml"/>
</p:declare-step>
```
Then you can launch the example with
```
java innovimax.quixproc.codex.drivers.open.QuiXProcB add-attribute.xpl
```
or
```
java innovimax.quixproc.codex.drivers.open.QuiXProcC add-attribute.xpl
```
And you should get the following result in the file `root-with-attribute.xml`:
```
<root foo="bar"/>
```