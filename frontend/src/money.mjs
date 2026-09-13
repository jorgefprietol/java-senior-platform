export function parseAmount(input) {
 if (!/^-?(?:0|[1-9]\d{0,13})(?:\.\d{1,2})?$/.test(input)) throw new Error('Enter an amount with up to two decimal places.');
 const [whole, fraction = ''] = input.replace('-', '').split('.');
 const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
 if (minor === 0n) throw new Error('Amount must not be zero.');
 return (input.startsWith('-') ? '-' : '') + whole + '.' + fraction.padEnd(2, '0');
}

// API decimals stay strings. BigInt represents minor units throughout the UI.
function minorUnits(value) {
 if (!/^-?\d+(?:\.\d{1,2})?$/.test(value)) throw new Error('Invalid decimal from API.');
 const [whole, fraction = ''] = value.replace('-', '').split('.');
 return (value.startsWith('-') ? -1n : 1n) * (BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0')));
}
function decimal(value) {
 const absolute = value < 0n ? -value : value;
 return (value < 0n ? '-' : '') + (absolute / 100n) + '.' + (absolute % 100n).toString().padStart(2, '0');
}
export function sumAmounts(values) { return decimal(values.reduce((sum, value) => sum + minorUnits(value), 0n)); }
export function positiveAmount(value) { return minorUnits(value) > 0n; }
export function formatMoney(value, currency = '') {
 const [whole, fraction] = decimal(minorUnits(value)).split('.');
 const symbol = currency === 'USD' ? '$' : currency === 'EUR' ? '€' : '';
 return (whole.startsWith('-') ? '-' : '') + symbol + whole.replace('-', '').replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + fraction;
}
