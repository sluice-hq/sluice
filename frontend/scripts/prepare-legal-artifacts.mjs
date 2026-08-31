import {
  copyFileSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  writeFileSync,
} from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(frontendRoot, '..');
const outputRoot = resolve(frontendRoot, '.next');
const noticeSource = resolve(repositoryRoot, 'THIRD_PARTY_NOTICES.md');
const noticeDestination = resolve(outputRoot, 'THIRD_PARTY_NOTICES.md');
const licensesDestination = resolve(outputRoot, 'THIRD_PARTY_LICENSES.txt');
const packageJson = JSON.parse(
  readFileSync(resolve(frontendRoot, 'package.json'), 'utf8'),
);

mkdirSync(outputRoot, { recursive: true });
copyFileSync(noticeSource, noticeDestination);

const sections = [
  'Sluice frontend direct runtime dependency licenses',
  '',
  'Generated from the license and notice files shipped in the exact installed npm packages.',
];

for (const packageName of Object.keys(packageJson.dependencies).sort()) {
  const packageRoot = resolve(frontendRoot, 'node_modules', ...packageName.split('/'));
  const metadata = JSON.parse(
    readFileSync(resolve(packageRoot, 'package.json'), 'utf8'),
  );
  const legalFiles = readdirSync(packageRoot, { withFileTypes: true })
    .filter(
      (entry) =>
        entry.isFile() && /^(license|notice|copying)(?:\.|$)/i.test(entry.name),
    )
    .map((entry) => entry.name)
    .sort((left, right) => left.localeCompare(right));

  if (legalFiles.length === 0) {
    throw new Error(
      `No license or notice file found for direct runtime dependency ${packageName}@${metadata.version}`,
    );
  }

  const declaredLicense =
    typeof metadata.license === 'string'
      ? metadata.license
      : JSON.stringify(metadata.license ?? metadata.licenses ?? 'not declared');

  sections.push(
    '',
    '='.repeat(80),
    `${packageName}@${metadata.version}`,
    `Declared license: ${declaredLicense}`,
    `Source files: ${legalFiles.join(', ')}`,
  );

  for (const legalFile of legalFiles) {
    sections.push(
      '',
      `--- ${legalFile} ---`,
      readFileSync(resolve(packageRoot, legalFile), 'utf8').trimEnd(),
    );
  }
}

writeFileSync(licensesDestination, `${sections.join('\n')}\n`, 'utf8');
