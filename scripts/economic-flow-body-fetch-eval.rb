#!/usr/bin/env ruby

require "cgi"
require "json"
require "net/http"
require "uri"

BODY = /class="story-news article"(.*?)<p class="txt-copyright/m
PARAGRAPH = /<p(?:\s[^>]*)?>(.*?)<\/p>/m

def paragraphs(url)
  uri = URI(url)
  request = Net::HTTP::Get.new(uri)
  request["User-Agent"] = "EconomicBriefing/1.0"
  response = Net::HTTP.start(uri.host, uri.port, use_ssl: true, open_timeout: 10, read_timeout: 30) { |http| http.request(request) }
  raise "HTTP #{response.code}: #{url}" unless response.is_a?(Net::HTTPSuccess)

  body = response.body.force_encoding("UTF-8")[BODY, 1].to_s
  body.scan(PARAGRAPH).flatten.map do |html|
    CGI.unescapeHTML(html.gsub(/<br\s*\/?\s*>/i, "\n").gsub(/<[^>]+>/, " ").gsub(/[ \t\r]+/, " ").strip)
  end.reject do |text|
    text.empty? || text.include?("무단 전재") || text.include?("AI 학습") || text.match?(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
  end
end

selection_path, output_path, reusable_path = ARGV
abort "selection and output paths are required" unless selection_path && output_path
selection = JSON.parse(File.read(selection_path))
reusable = reusable_path && File.exist?(reusable_path) ? JSON.parse(File.read(reusable_path)).fetch("articles") : []
reusable_by_story = reusable.to_h { |article| [article.fetch("storyKey"), article.fetch("paragraphs")] }
saved = File.exist?(output_path) ? JSON.parse(File.read(output_path)) : {
  "targetDate" => File.basename(selection_path)[/\d{4}-\d{2}-\d{2}/], "articles" => []
}
done = saved.fetch("articles").map { |article| article.fetch("storyKey") }

selection.fetch("selected").reject { |article| done.include?(article.fetch("storyKey")) }.each do |article|
  content = reusable_by_story[article.fetch("storyKey")] || paragraphs(article.fetch("url"))
  raise "body extraction failed: #{article.fetch('url')}" if content.size < 2

  saved.fetch("articles") << article.merge("paragraphs" => content)
  File.write(output_path, JSON.pretty_generate(saved))
  puts "#{article.fetch('id')} paragraphs=#{content.size} reused=#{reusable_by_story.key?(article.fetch('storyKey'))}"
end
