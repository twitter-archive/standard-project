#!/usr/bin/env ruby

# This is the scala codegen.  It works by reflecting The generated ruby.

$VERBOSE = nil # Ruby thrift bindings always are noisy

require "fileutils"
require "erb"
require "set"
include FileUtils

# Stub out thrift.  We just need to have the generated ruby compile, not
# actually run in this context.
module ::Thrift
  module Client; end
  module Processor; end 
  module Struct
    def generate_accessors(*args); end
    def method_missing(*args);end
    extend self
  end
  module Struct_Union; end
  module Types
    BOOL   = 0
    BYTE   = 1
    I16    = 2
    I32    = 3
    I64    = 4
    DOUBLE = 5
    STRING = 6
    MAP    = 7
    SET    = 8
    LIST   = 9
    STRUCT = 10
  end
  
  class Exception; end
  
  class ApplicationException
    MISSING_RESULT = nil
    def initialize(*args)
    end
  end
end
def require(*args); end

# Utility stolen from activesupport
class String
  def camelize(first_letter_in_uppercase = false)
    if first_letter_in_uppercase
      gsub(/\/(.?)/) { "::#{$1.upcase}" }.gsub(/(?:^|_)(.)/) { $1.upcase }
    else
      self[0].chr.downcase + camelize(self)[1..-1]
    end
  end    
end

# These functions are macros for common patterns in the generated scala.

def type_of(field, thrifty = false, nested = false)
  return "Void" if field.nil?
  j = thrifty && nested ? "java.lang." : ""
  base = case field[:type]
  when ::Thrift::Types::I32: 
    if field[:enum_class]
      thrifty ? 
      $tnamespace + "." + last(field[:enum_class]) :
      last(field[:enum_class])
    else
      # thrifty ? "java.lang.Integer" : 
      "Int"
    end
  when ::Thrift::Types::STRUCT: thrifty ? $tnamespace + "." + last(field[:class]) : last(field[:class])
  when ::Thrift::Types::STRING: 
    field[:binary] ? "java.nio.ByteBuffer" : "String"
  when ::Thrift::Types::BOOL: "#{j}Boolean"
  when ::Thrift::Types::I16: "#{j}Short"
  when ::Thrift::Types::I64: "#{j}Long"
  when ::Thrift::Types::BYTE: "#{j}Byte"
  when ::Thrift::Types::DOUBLE: "#{j}Double"
  when ::Thrift::Types::SET:
    tmp = "Set[#{type_of(field[:element], thrifty, true)}]"
    thrifty ? "J#{tmp}" : tmp
  when ::Thrift::Types::MAP:
    tmp = "Map[#{type_of(field[:key], thrifty, true)}, #{type_of(field[:value], thrifty, true)}]"
    thrifty ? "J#{tmp}" : tmp
  when ::Thrift::Types::LIST:
    tmp = "List[#{type_of(field[:element], thrifty, true)}]"
    thrifty ? "J#{tmp}" : tmp
  else
    throw "unknown field type: #{field[:type]}"
  end
  field[:optional] ? "Option[#{base}]" : base
end  

def unwrapper(f, nested = false)
  pre = ""
  post = ""
  case f[:type]
  when ::Thrift::Types::DOUBLE: 
    if nested 
      pre += "new java.lang.Double("
      post += ")"
    end
  when ::Thrift::Types::I64: 
    if nested 
      pre += "new java.lang.Long("
      post += ")"
    end
  when ::Thrift::Types::BYTE: 
    if nested 
      pre += "new java.lang.Byte("
      post += ")"
    end
  when ::Thrift::Types::BOOL: 
    if nested 
      pre += "new java.lang.Boolean("
      post += ")"
    end
  when ::Thrift::Types::SET: 
    pre += "asJavaSet(("
    a, b = unwrapper(f[:element], true)
    post += ").map(x => #{a}x#{b}))"
  when ::Thrift::Types::LIST: 
    pre += "asJavaList(("
    a, b = unwrapper(f[:element], true)
    post += ").map(x => #{a}x#{b}))"
  when ::Thrift::Types::MAP: 
    pre += "asJavaMap(("
    a, b = unwrapper(f[:key], true)
    c, d = unwrapper(f[:value], true)
    post += ").map(x => (#{a}x._1#{b}, #{c}x._2#{d})).toMap)"
  when ::Thrift::Types::I32: 
    post += ".toThrift" if f[:enum_class]
  when ::Thrift::Types::STRUCT: 
    post += ".toThrift"
  end
  [pre, post]
end

def unwrap(f, inner = nil)
  pre, post = unwrapper(f)
  if inner
    pre + inner + post
  else
    @output << pre
    yield
    @output << post
  end
end

def wrapper(f, name = nil, nested = false)
  name ||= f[:name].camelize
  case f[:type]
  when ::Thrift::Types::I32: 
    if f[:enum_class]
      "#{last(f[:enum_class])}(#{name})"
    else
      name
    end
  when ::Thrift::Types::BYTE: "#{name}.byteValue"
  when ::Thrift::Types::I64: "#{name}.longValue"
  when ::Thrift::Types::BOOL: "#{name}.booleanValue"
  when ::Thrift::Types::DOUBLE: "#{name}.doubleValue"
  when ::Thrift::Types::LIST: "asScalaBuffer(#{name}).view.map(x=>#{wrapper(f[:element], "x", true)}).toList"
  when ::Thrift::Types::SET: "Set(asScalaSet(#{name}).view.map(x=>#{wrapper(f[:element], "x", true)}).toSeq: _*)"
  when ::Thrift::Types::MAP: "Map((#{name}).view.map(x=>(#{wrapper(f[:key], "x._1", true)}, #{wrapper(f[:value], "x._2", true)})).toSeq: _*)"
  when ::Thrift::Types::STRUCT: "new #{last(f[:class])}(#{name})"
  else
    name
  end
end

def last(klass) 
  klass.to_s.split("::").pop
end

MStruct = Struct.new(:name, :args, :retval)

module Codegen
  def run(input, output, tnamespace, rnamespace, namespace)
    output = File.expand_path(File.join(output, *namespace.split(".")))
    mkdir_p output
    $tnamespace = tnamespace

    # Hooray, we generate the scala with ERb
    service_template_string = '
      package <%=namespace %>

      import java.util.{List => JList, Map => JMap, Set => JSet}
      import scala.collection.mutable._
      import scala.collection.JavaConversions._
      import com.twitter.util.Future
      import com.twitter.util.TimeConversions._
      import com.twitter.finagle.thrift._
      import org.apache.thrift.protocol._
      import org.slf4j._
      import com.twitter.admin.Service
      import com.twitter.util._
      import com.twitter.finagle.builder._
      import java.net.InetSocketAddress
      import com.twitter.finagle.stats._
      

      // Autogenerated

      trait <%=obj%> {
        
        val log = LoggerFactory.getLogger(getClass)
        
        <% for m in methods do %>
          def <%=m.name.camelize%>(<%=m.args.map{|f| f[:name].camelize + ": " + type_of(f)}.join(", ") %>): Future[<%=type_of(m.retval)%>]
        <% end %>
        
        def toThrift = new <%=obj%>ThriftAdapter(this)        
      }
      
      trait <%=obj%>Server extends com.twitter.admin.Service with <%=obj%>{
        def thriftCodec = ThriftServerFramedCodec()
        val thriftProtocolFactory = new TBinaryProtocol.Factory()
        val thriftPort: Int
        val serverName: String
        
        var server: Server = null
        
        def start = {
          val thriftImpl = new <%=tnamespace%>.<%=obj%>.Service(toThrift, thriftProtocolFactory)
          val serverAddr = new InetSocketAddress(thriftPort)
          server = ServerBuilder().codec(thriftCodec).name(serverName).reportTo(new OstrichStatsReceiver).bindTo(serverAddr).build(thriftImpl)
        }

        def shutdown = synchronized {
          if (server != null) {
            server.close(0.seconds)
          }
        }
      }

      class <%=obj%>ThriftAdapter(val <%=obj.to_s.camelize%>: <%=obj%>) extends <%=tnamespace%>.<%=obj%>.ServiceIface { 
        val logger = LoggerFactory.getLogger(getClass)
        <% for m in methods do %>
          def <%=m.name.downcase%>(<%=m.args.map{|f| f[:name].camelize + ": " + type_of(f, true)}.join(", ") %>) = <%="try" if $exception %> {
            <%=obj.to_s.camelize%>.<%=m.name.camelize%>(<%=m.args.map{|f| wrapper(f) }.join(", ")%>)
            <% if m.retval %>
              .map { retval =>
                <% unwrap(m.retval) do %>retval<%end%>
              }
            <% end %>
          <% if $exception %>
            } catch {
              case t: Throwable => {
                logger.error("uncaught error", t)
                throw new <%=tnamespace%>.<%=last $exception%>(t.getMessage)
              }
          <% end %>
          }
        <% end %>
      }
      
      class <%=obj%>ClientAdapter(val <%=obj.to_s.camelize%>: <%=tnamespace%>.<%=obj%>.ServiceIface) extends <%=obj%> { 
        val logger = LoggerFactory.getLogger(getClass)
        <% for m in methods do %>
          def <%=m.name.camelize%>(<%=m.args.map{|f| f[:name].camelize + ": " + type_of(f)}.join(", ") %>) = {
            <%=obj.to_s.camelize%>.<%=m.name.downcase%>(<%=m.args.map{|f| unwrap(f, f[:name]).camelize }.join(", ")%>)
            <% if m.retval %>
              .map { retval =>
                <%=wrapper(m.retval, "retval") %>
              }
            <% end %>
          }
        <% end %>
      }
      
    '
    enum_template_string = '
      package <%=namespace %>

      // Autogenerated

      abstract case class <%=obj%>(id: Int) {
        def name: String
        def toThrift = <%=tnamespace %>.<%=obj%>.findByValue(id)
      }
      object <%=obj%> {
        def apply(thrifty: <%=tnamespace %>.<%=obj%>): <%=obj%> = apply(thrifty.getValue)
        
        def apply(id: Int): <%=obj%> = id match {
          <% values.values.each do |v| %> 
            case <%=v.downcase.camelize(true)%>.id => <%=v.downcase.camelize(true)%>
          <% end %>
        }
        
        def apply(name: String): <%=obj%> = name match {
          <% values.values.each do |v| %> 
            case "<%=v.downcase.camelize(true)%>" => <%=v.downcase.camelize(true)%>
          <% end %>
        }
        
        <% values.each do |k, v| %> 
          object <%=v.downcase.camelize(true)%> extends <%=obj%>(<%=k%>) {
            val name = "<%=v.downcase.camelize(true)%>"
          }
        <% end %>
      }
    '
    
    struct_template_string = '
      package <%=namespace %>
      
      import java.util.{List => JList, Map => JMap, Set => JSet}
      import scala.collection.mutable._
      import scala.collection.JavaConversions._

      // Autogenerated

      case class <%=obj%>(<%=fields.map{|f| f[:name].camelize + ": " + type_of(f)}.join(", ") %>) <%="extends Exception" if is_exception %>{
        def this(thrifty: <%=tnamespace%>.<%=obj%>) = this(
          <% for f in fields do %>
            <%="," unless f == fields.first %>
            <% if f[:optional] %>
              if(thrifty.isSet<%=f[:name].capitalize%>) {
                Some(<%= wrapper(f, "thrifty.#{f[:name]}")%>)
              } else {
                None
              }
            <% else %>
              <%= wrapper(f, "thrifty.#{f[:name]}")%>
            <% end %>
          <% end %>
        )

        <% if fields.any?{|f| f[:optional]} %>
          def this(<%=fields.map{|f| f[:name].camelize + ": " + type_of(f) unless f[:optional] }.compact.join(", ") %>) = this(<%=fields.map{|f| f[:optional] ? "None" : f[:name].camelize }.join(", ") %>)
        <% end %>

        def toThrift() = {
          val out = new <%=tnamespace%>.<%=obj%>()
          <% for f in fields do %>
            <%="#{f[:name].camelize}.foreach { #{f[:name].camelize} => " if f[:optional] %>
              out.set<%=f[:name].capitalize%>(<%unwrap(f) do %><%=f[:name].camelize%><% end %>)
            <%="}" if f[:optional] %>
          <% end %>
          out
        }
      }
    '

    # Load the thrift files
    Dir["#{input}/*types.rb"].each {|f| load f }
    Dir["#{input}/*.rb"].each {|f| load f }

    # Scan looking for...
    root = eval(rnamespace)
    root.constants.each do |name|
      obj = root.const_get(name)
      if obj.const_defined?(:VALUE_MAP)
        # I'm an enum
        values = obj::VALUE_MAP
        
        obj = last obj
        template = ERB.new(enum_template_string, nil, nil, "@output")
        File.open("#{output}/#{obj}.scala", "w") {|f| f.print(template.result(binding)) }
      end
    end
    
    root.constants.each do |name|
      obj = root.const_get(name)
      if obj.method_defined?(:struct_fields) 
        fields = obj.new.struct_fields.to_a.sort_by{|f| f.first}.map{|f| f.last }
        # We assume that you'll have a single thrift exception type for your app.
        is_exception = obj.superclass == ::Thrift::Exception  
        $exception = obj if is_exception
        
        obj = last obj
        template = ERB.new(struct_template_string, nil, nil, "@output")
        File.open("#{output}/#{obj}.scala", "w") {|f| f.print(template.result(binding)) }
      end
    end
        
    root.constants.each do |name|
      obj = root.const_get(name)
      if obj.const_defined?(:Client)
        methods = obj.constants.map{|c| c.to_s[/(.*)_args$/, 1] }.compact.map(&:downcase).map {|name|
          out = MStruct.new
          out.name = name
          out.args = obj.const_get(name.capitalize + "_args").new.struct_fields.to_a.sort_by{|f| f.first}.map{|f| f.last }
          out.retval = obj.const_get(name.capitalize + "_result").new.struct_fields[0]
          out
        }
        obj = last obj
        template = ERB.new(service_template_string, nil, nil, "@output")
        File.open("#{output}/#{obj}.scala", "w") {|f| f.print(template.result(binding)) }
      end
    end
  end
  extend self
end

if ARGV.length == 5
  Codegen::run(*ARGV)
end