import tinycolor from 'tinycolor2'

function generateColorVariations(defaultColor: string) {
    const defaultColorObj = tinycolor(defaultColor)

    const darkColor = defaultColorObj.clone().darken(2)
    const baseLight = tinycolor('#ffffff')
    const lightColor = tinycolor
        .mix(baseLight, defaultColorObj.toHex(), 12)
        .toHexString()
    const mediumColor = defaultColorObj.clone().lighten(26)

    return {
        default: defaultColorObj.toHexString(),
        dark: darkColor.toHexString(),
        light: lightColor,
        medium: mediumColor.toHexString(),
    }
}

function generateSelectionColor(defaultColor: string) {
    const defaultColorObj = tinycolor(defaultColor)
    const lightColor = defaultColorObj.lighten(8)
    return lightColor.toHexString()
}

export function generateTheme({
    primaryColor,
    fullLogoUrl,
    favIconUrl,
    logoIconUrl,
    websiteName,
}: {
    primaryColor: string
    fullLogoUrl: string
    favIconUrl: string
    logoIconUrl: string
    websiteName: string
}) {
    return {
        websiteName,
        colors: {
            avatar: '#515151',
            'blue-link': '#1890ff',
            danger: '#f94949',
            primary: generateColorVariations(primaryColor),
            warn: {
                default: '#f78a3b',
                light: '#fff6e4',
                dark: '#cc8805',
            },
            success: {
                default: '#14ae5c',
                light: '#3cad71',
            },
            selection: generateSelectionColor(primaryColor),
        },
        logos: {
            fullLogoUrl,
            favIconUrl,
            logoIconUrl,
        },
    }
}

export const defaultTheme = generateTheme({
    // Workflow Station 品牌红 —— 与三端前端主题统一（platform 表无自定义色时的默认值）
    primaryColor: '#db0011',
    // HERMES-PATCH-010（docs/ap-integration/HERMES_PATCHES.md）
    // 白标：名称与 admin-center 的入口菜单一致（Automation Studio）。
    // logo 走同源相对路径（packages/web/public/），不再指向 cdn.activepieces.com——
    // 生产完全断网（X-2/X-3），外部 CDN 资源必然加载失败。
    websiteName: 'Automation Studio',
    fullLogoUrl: '/hermes-full-logo.svg',
    favIconUrl: '/hermes-mark.svg',
    logoIconUrl: '/hermes-mark.svg',
})
