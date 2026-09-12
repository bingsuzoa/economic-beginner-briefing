#!/usr/bin/env ruby

require "cgi"
require "date"
require "json"
require "net/http"
require "optparse"
require "set"
require "thread"
require "time"
require "uri"

SEARCH_URI = URI("https://ars.yna.co.kr/api/v2/search.basic")
DIVISIONS = %w[all 01 02 05 07 10 11 12 0712].freeze
PAGE_SIZE = 10
MAX_PAGE = 100

def clean(value)
  CGI.unescapeHTML(value.to_s.gsub(/<[^>]+>/, " ").gsub(/\s+/, " ").strip)
end

def get_json(uri, attempts: 3)
  request = Net::HTTP::Get.new(uri)
  request["User-Agent"] = "Mozilla/5.0 economic-flow-evaluation"
  request["Referer"] = "https://www.yna.co.kr/search/index"
  response = Net::HTTP.start(uri.host, uri.port, use_ssl: true, open_timeout: 10, read_timeout: 20) do |http|
    http.request(request)
  end
  raise "HTTP #{response.code}: #{uri}" unless response.is_a?(Net::HTTPSuccess)

  JSON.parse(response.body)
rescue StandardError
  raise if attempts <= 1

  sleep(4 - attempts)
  get_json(uri, attempts: attempts - 1)
end

def search_page(date, division, page)
  uri = SEARCH_URI.dup
  query = {
    cattr: "A", page_no: page, page_size: PAGE_SIZE, scope: "all", sort: "date",
    channel: "basic_kr", from: date.delete("-"), to: date.delete("-")
  }
  query[:div_code] = division unless division == "all"
  uri.query = URI.encode_www_form(query)
  get_json(uri).fetch("YIB_KR_A")
end

def article(item)
  published_at = Time.strptime(item.fetch("DATETIME"), "%Y%m%d%H%M%S").getlocal("+09:00")
  cid = item.fetch("CID")
  {
    "cid" => cid,
    "storyKey" => cid[/AKR\d{12}/] || cid,
    "title" => clean(item["EDIT_TITLE"].to_s.empty? ? item["TITLE"] : item["EDIT_TITLE"]),
    "summary" => clean(item["BODY"])[0, 500],
    "divisionCodes" => item["DIV_CODE"].to_s.split,
    "publishedAt" => published_at.iso8601,
    "url" => "https://www.yna.co.kr/view/#{cid}"
  }
end

options = { workers: 6, output: "/tmp/economic_flow_history_metadata_20260901_20260910.json" }
OptionParser.new do |parser|
  parser.banner = "Usage: ruby scripts/economic-flow-history-eval.rb --from DATE --to DATE"
  parser.on("--from DATE") { |value| options[:from] = Date.iso8601(value) }
  parser.on("--to DATE") { |value| options[:to] = Date.iso8601(value) }
  parser.on("--workers N", Integer) { |value| options[:workers] = value }
  parser.on("--output PATH") { |value| options[:output] = value }
end.parse!
abort "--from and --to are required" unless options[:from] && options[:to]
abort "--to must be on or after --from" if options[:to] < options[:from]

dates = (options[:from]..options[:to]).map(&:iso8601)
results = Hash.new { |hash, key| hash[key] = {} }
seen_by_division = Hash.new { |hash, key| hash[key] = Set.new }
fetched_by_division = Hash.new(0)
expected_by_division = {}
errors = []
jobs = Queue.new
lock = Mutex.new

dates.each do |date|
  DIVISIONS.each do |division|
    first = search_page(date, division, 1)
    lock.synchronize do
      first.fetch("result", []).each do |item|
        results[date][item.fetch("CID")] = article(item)
        seen_by_division[[date, division]] << item.fetch("CID")
        fetched_by_division[[date, division]] += 1
      end
    end
    pages = [(first.fetch("totalCount", 0).to_f / PAGE_SIZE).ceil, MAX_PAGE].min
    expected_by_division[[date, division]] = first.fetch("totalCount", 0)
    puts "#{date} division=#{division} articles=#{first.fetch('totalCount', 0)} pages=#{pages}"
    (2..pages).each { |page| jobs << [date, division, page] }
  end
end

workers = Array.new(options[:workers]) do
  Thread.new do
    loop do
      date, division, page = jobs.pop(true)
      page_result = search_page(date, division, page)
      lock.synchronize do
        page_result.fetch("result", []).each do |item|
          results[date][item.fetch("CID")] = article(item)
          seen_by_division[[date, division]] << item.fetch("CID")
          fetched_by_division[[date, division]] += 1
        end
      end
    rescue ThreadError
      break
    rescue StandardError => e
      lock.synchronize { errors << "#{date} division=#{division} page=#{page}: #{e.message}" }
    end
  end
end
workers.each(&:join)
abort errors.join("\n") unless errors.empty?

current_date = Time.now.getlocal("+09:00").to_date.iso8601
coverage_errors = expected_by_division.each_with_object([]) do |(key, expected), list|
  next if key.last == "all"

  fetched = fetched_by_division[key]
  next if key.first == current_date

  list << "#{key.join('/')} expected=#{expected} fetched=#{fetched}" unless expected == fetched
end
abort coverage_errors.join("\n") unless coverage_errors.empty?

union_errors = dates.each_with_object([]) do |date, list|
  next if date == current_date

  expected = expected_by_division.fetch([date, "all"])
  actual = results.fetch(date).size
  list << "#{date} all expected=#{expected} merged=#{actual}" unless expected == actual
end
warn union_errors.join("\n") unless union_errors.empty?

current_day_changes = expected_by_division.filter do |(date, _division), expected|
  date == current_date && fetched_by_division[[date, _division]] != expected
end
warn "current-day index changed during collection; result is a partial snapshot" unless current_day_changes.empty?

payload = {
  "capturedAt" => Time.now.getlocal("+09:00").iso8601,
  "source" => "Yonhap search API, all news divisions merged by article id",
  "allCoverage" => dates.to_h do |date|
    expected = expected_by_division.fetch([date, "all"])
    merged = results.fetch(date).size
    [date, { "expectedHits" => expected, "mergedArticles" => merged, "complete" => expected == merged }]
  end,
  "coverage" => expected_by_division.to_h do |(date, division), expected|
    key = [date, division]
    ["#{date}/#{division}", {
      "expectedHits" => expected,
      "fetchedHits" => fetched_by_division[key],
      "uniqueArticles" => seen_by_division[key].size
    }]
  end,
  "dates" => dates.to_h do |date|
    items = results.fetch(date).values.sort_by { |item| [item.fetch("publishedAt"), item.fetch("cid")] }
    [date, { "rawArticles" => items.size, "articles" => items }]
  end
}
File.write(options[:output], JSON.pretty_generate(payload))
puts "PASS coverage=complete dates=#{dates.size} divisions=#{expected_by_division.size}"
puts "saved=#{options[:output]} total=#{payload.fetch('dates').values.sum { |day| day.fetch('rawArticles') }}"
