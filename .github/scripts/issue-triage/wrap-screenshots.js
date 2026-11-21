// Wraps screenshots in an issue body in a collapsed <details> block, so long screenshots
// do not fill the page. Each group of screenshot lines, which may be separated by blank lines,
// gets its own block. Screenshots that are already in a <details> block or in a code block,
// and images in a line with other text, are left as they are.

// <img ...> or ![alt](url)
const IMAGE = /<img\b[^>]*>|!\[[^\]\n]*\]\([^)\n]*\)/gi;

function isImageLine(line) {
  const images = line.match(IMAGE);
  return images !== null && line.replace(IMAGE, '').trim() === '';
}

function screenshotBlock(lines) {
  const count = lines.reduce((total, line) => total + (line.match(IMAGE) || []).length, 0);
  return [
    '<details>',
    `<summary>${count === 1 ? 'Screenshot' : 'Screenshots'}</summary>`,
    // The Markdown inside the block only renders with a blank line after the summary.
    '',
    ...lines,
    '',
    '</details>',
  ];
}

/**
 * Returns the body with its screenshots wrapped in <details> blocks, or the same body if there is nothing to wrap.
 */
function wrapScreenshots(body) {
  if (!body) return body;

  const lines = body.split(/\r?\n/);
  const result = [];
  let fence = null;
  let detailsDepth = 0;
  let group = [];
  // Blank lines after the last screenshot of a group, added back if no screenshot follows.
  let pendingBlank = [];

  const flush = () => {
    if (group.length > 0) {
      result.push(...screenshotBlock(group));
      group = [];
    }
    result.push(...pendingBlank);
    pendingBlank = [];
  };

  for (const line of lines) {
    const fenceMatch = /^\s*(`{3,}|~{3,})/.exec(line);
    if (fence) {
      if (fenceMatch && fenceMatch[1][0] === fence[0] && fenceMatch[1].length >= fence.length) fence = null;
      result.push(line);
      continue;
    }

    if (detailsDepth === 0 && isImageLine(line)) {
      // Blank lines between screenshots stay inside the block.
      group.push(...pendingBlank, line.trim());
      pendingBlank = [];
      continue;
    }

    if (group.length > 0 && line.trim() === '') {
      pendingBlank.push(line);
      continue;
    }

    flush();

    if (fenceMatch) {
      fence = fenceMatch[1];
    } else {
      detailsDepth += (line.match(/<details\b/gi) || []).length;
      detailsDepth -= (line.match(/<\/details>/gi) || []).length;
      if (detailsDepth < 0) detailsDepth = 0;
    }
    result.push(line);
  }
  flush();

  const wrapped = result.join('\n');
  return wrapped === lines.join('\n') ? body : wrapped;
}

module.exports = { wrapScreenshots };
