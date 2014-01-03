version = File.read(File.expand_path('../VERSION', __FILE__)).strip

Gem::Specification.new do |s|
  s.name        = 'killbill-stripe'
  s.version     = version
  s.summary     = 'Plugin to use Stripe as a gateway.'
  s.description = 'Kill Bill payment plugin for Stripe.'

  s.required_ruby_version = '>= 1.9.3'

  s.license = 'Apache License (2.0)'

  s.author   = 'Kill Bill core team'
  s.email    = 'killbilling-users@googlegroups.com'
  s.homepage = 'http://kill-bill.org'

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.bindir        = 'bin'
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]

  s.rdoc_options << '--exclude' << '.'

  s.add_dependency 'killbill', '~> 1.9.0'
  s.add_dependency 'activemerchant', '~> 1.42.3'
  s.add_dependency 'activerecord', '~> 3.2.1'
  s.add_dependency 'sinatra', '~> 1.3.4'
  if defined?(JRUBY_VERSION)
    s.add_dependency 'activerecord-jdbcmysql-adapter', '~> 1.2.9'
  end

  s.add_development_dependency 'jbundler', '~> 0.4.1'
  s.add_development_dependency 'rake', '>= 10.0.0'
  s.add_development_dependency 'rspec', '~> 2.12.0'
  if defined?(JRUBY_VERSION)
    s.add_development_dependency 'activerecord-jdbcsqlite3-adapter', '~> 1.2.6'
  else
    s.add_development_dependency 'sqlite3', '~> 1.3.7'
  end
end
