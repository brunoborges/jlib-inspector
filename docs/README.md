# JLib Inspector Documentation Site

This folder contains the source files for the JLib Inspector GitHub Pages documentation site.

## 📁 Structure

```
site/
├── _config.yml           # Jekyll configuration
├── _layouts/             # Page layouts
│   └── default.html      # Main layout template
├── index.md              # Homepage
├── getting-started.md    # Getting started guide
├── agent.md              # Agent documentation
├── server.md             # Server documentation
├── screenshots.md        # Screenshots gallery
├── javadoc.md            # Javadoc integration page
├── javadoc/              # Generated Javadoc (auto-populated)
│   └── index.html        # Javadoc placeholder
├── *.png                 # Screenshot images
└── README.md             # This file
```

## 🚀 Building the Site

### Local Development

To build and preview the site locally:

```bash
# Install Jekyll (if not already installed)
gem install bundler jekyll

# Navigate to site directory
cd site

# Create Gemfile (first time only)
echo "source 'https://rubygems.org'" > Gemfile
echo "gem 'github-pages', group: :jekyll_plugins" >> Gemfile

# Install dependencies
bundle install

# Serve locally
bundle exec jekyll serve

# Open in browser
open http://localhost:4000/jlib-inspector
```

### GitHub Pages Deployment

The site is automatically deployed to GitHub Pages when changes are pushed to the main branch. The site will be available at:

**https://brunoborges.github.io/jlib-inspector**

### Manual Deployment

To manually trigger deployment:

1. Push changes to the `main` branch
2. Go to repository Settings → Pages
3. Ensure source is set to "Deploy from a branch"
4. Select "main" branch and "/ (root)" folder
5. The site will build automatically

## 📖 Content Management

### Adding New Pages

1. Create a new `.md` file in the `site/` directory
2. Add front matter with layout and title:
   ```yaml
   ---
   layout: default
   title: Your Page Title
   ---
   ```
3. Add navigation link to `_config.yml` or layout template
4. Commit and push changes

### Updating Documentation

1. Edit the relevant `.md` file
2. Update screenshots by replacing `.png` files
3. Regenerate Javadoc if needed:
   ```bash
   # From project root
   ./mvnw javadoc:aggregate
   cp -r target/site/apidocs/* site/javadoc/
   ```
4. Commit and push changes

### Managing Screenshots

Screenshot files are stored directly in the `site/` folder:
- `screenshot1.png` - Main dashboard view
- `screenshot2.png` - Application details view  
- `warning-banner-screenshot.png` - Experimental warning

To update screenshots:
1. Replace the relevant `.png` file
2. Update references in `screenshots.md` if needed
3. Commit and push changes

## 🎨 Customization

### Theme and Styling

The site uses a custom layout (`_layouts/default.html`) with embedded CSS. To modify:

1. Edit the `<style>` section in `_layouts/default.html`
2. Modify CSS custom properties in `:root` for color scheme
3. Update responsive breakpoints in `@media` queries

### Configuration

Site settings are managed in `_config.yml`:
- Site title and description
- GitHub repository information
- Navigation links
- Jekyll plugins and settings

### Navigation

Update navigation links by modifying:
1. `_config.yml` - for data-driven navigation
2. `_layouts/default.html` - for hardcoded navigation

## 🔧 Integrations

### Javadoc Integration

The site integrates with Maven-generated Javadoc:

1. Javadoc is generated via: `./mvnw javadoc:aggregate`
2. Output is copied to `site/javadoc/` directory
3. `javadoc.md` provides overview and links

### Maven Site Integration

The documentation can be integrated with Maven site:

```bash
# Generate Maven site (includes Javadoc)
./mvnw site

# Copy to GitHub Pages structure
cp -r target/site/* site/
```

### GitHub Actions

Consider adding a GitHub Action for automated deployment:

```yaml
name: Deploy Documentation
on:
  push:
    branches: [ main ]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Java
        uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Generate Javadoc
        run: ./mvnw javadoc:aggregate
      - name: Copy Javadoc
        run: cp -r target/site/apidocs/* site/javadoc/
      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./site
```

## 📋 Maintenance

### Regular Updates

- **Documentation**: Keep content synchronized with code changes
- **Screenshots**: Update when UI changes significantly  
- **Javadoc**: Regenerate when API changes
- **Dependencies**: Update Jekyll and plugins regularly

### Content Review

Periodically review:
- Accuracy of setup instructions
- Relevance of examples and code snippets
- Working status of all links
- Image quality and relevance

### SEO and Accessibility

The site includes:
- Semantic HTML structure
- Responsive design
- Proper heading hierarchy
- Alt text for images (add when updating)
- Meta descriptions (can be enhanced)

## 🔗 Related Files

- **Root README.md** - Main project documentation
- **DOCKER.md** - Docker-specific documentation  
- **LICENSE** - Project license information
- **.github/copilot-instructions.md** - Development guidelines

## 🤝 Contributing

To contribute to the documentation:

1. Fork the repository
2. Create a feature branch
3. Make your changes in the `site/` directory
4. Test locally with Jekyll
5. Submit a pull request

For major documentation restructuring, please open an issue first to discuss the changes.